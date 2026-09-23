using System.Security.Cryptography;
using VRCX.MobileCompanion.Session;

namespace VRCX.MobileCompanion.Security;

public sealed record DeviceRecord(string DeviceId, string DeviceName, string AccountId,
    string TokenHash, DateTimeOffset AddedAt, DateTimeOffset? RevokedAt);
public sealed record DeviceSummary(string DeviceId, string DeviceName, string AccountId,
    DateTimeOffset AddedAt, bool Revoked);
public sealed record DeviceCredential(string DeviceId, string Token);
public enum DeviceAuthorization { Authorized, Unauthorized, WrongAccount, Revoked }

public interface IDeviceStateStore
{
    IReadOnlyList<DeviceRecord> Load();
    void Save(IReadOnlyList<DeviceRecord> state);
}

public sealed class DeviceRegistry(IDeviceStateStore stateStore, TimeProvider? clock = null)
{
    private readonly object _gate = new();
    private readonly TimeProvider _clock = clock ?? TimeProvider.System;
    private readonly List<DeviceRecord> _devices = stateStore.Load().ToList();

    public DeviceCredential Issue(string deviceName, string accountId)
    {
        if (!MobileSession.IsValidUserId(accountId))
            throw new ArgumentException("Invalid account ID", nameof(accountId));
        var token = RandomUrlSafe(32);
        var deviceId = RandomUrlSafe(16);
        var hash = Convert.ToBase64String(SHA256.HashData(DecodeUrlSafe(token)));
        lock (_gate)
        {
            _devices.Add(new DeviceRecord(deviceId, deviceName, accountId, hash, _clock.GetUtcNow(), null));
            stateStore.Save(_devices.ToArray());
        }
        return new DeviceCredential(deviceId, token);
    }

    public DeviceAuthorization Authorize(string? token, string accountId)
    {
        if (!TryDecodeUrlSafe(token, out var raw) || raw.Length != 32)
            return DeviceAuthorization.Unauthorized;
        var candidate = SHA256.HashData(raw);
        DeviceRecord? match = null;
        lock (_gate)
        {
            foreach (var device in _devices)
            {
                var expected = Convert.FromBase64String(device.TokenHash);
                if (CryptographicOperations.FixedTimeEquals(candidate, expected))
                    match = device;
            }
        }
        if (match is null) return DeviceAuthorization.Unauthorized;
        if (match.RevokedAt is not null) return DeviceAuthorization.Revoked;
        return match.AccountId == accountId ? DeviceAuthorization.Authorized : DeviceAuthorization.WrongAccount;
    }

    public IReadOnlyList<DeviceSummary> ListDevices()
    {
        lock (_gate)
            return _devices.Select(item => new DeviceSummary(item.DeviceId, item.DeviceName,
                item.AccountId, item.AddedAt, item.RevokedAt is not null)).ToArray();
    }

    public void Revoke(string deviceId)
    {
        lock (_gate)
        {
            var index = _devices.FindIndex(item => item.DeviceId == deviceId);
            if (index < 0) return;
            _devices[index] = _devices[index] with { RevokedAt = _clock.GetUtcNow() };
            stateStore.Save(_devices.ToArray());
        }
    }

    internal static string RandomUrlSafe(int bytes) => Convert.ToBase64String(RandomNumberGenerator.GetBytes(bytes))
        .TrimEnd('=').Replace('+', '-').Replace('/', '_');

    internal static byte[] DecodeUrlSafe(string token) => Convert.FromBase64String(token.Replace('-', '+')
        .Replace('_', '/').PadRight((token.Length + 3) / 4 * 4, '='));

    internal static bool TryDecodeUrlSafe(string? token, out byte[] bytes)
    {
        bytes = [];
        if (string.IsNullOrEmpty(token) || token.Length > 128 ||
            token.Any(c => !char.IsAsciiLetterOrDigit(c) && c is not '-' and not '_')) return false;
        try { bytes = DecodeUrlSafe(token); return true; }
        catch (FormatException) { return false; }
    }
}
