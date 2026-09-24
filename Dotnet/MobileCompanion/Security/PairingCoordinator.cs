using System.Net;
using System.Security.Cryptography;
using VRCX.MobileCompanion.Session;

namespace VRCX.MobileCompanion.Security;

public sealed record PairingOffer(int V, string Address, int Port, string Host,
    string SpkiSha256, string Secret);
public sealed record PairingRequestReceipt(string RequestId, string PollSecret);
public sealed record PendingPairing(string RequestId, string DeviceName, DateTimeOffset ExpiresAt);
public sealed record PairingRedeemResult(string State, string? DeviceId, string? Token);

public sealed class InvalidPairingException : Exception;
public sealed class ExpiredOfferException : Exception;
public sealed class RejectedPairingException : Exception;
public sealed class PairingRateLimitException : Exception;

public sealed class PairingCoordinator(string spkiSha256, DeviceRegistry devices, TimeProvider? clock = null)
{
    private sealed record OfferState(byte[] SecretHash, DateTimeOffset ExpiresAt);
    private sealed class RequestState(string id, string name, byte[] pollHash, DateTimeOffset expiresAt)
    {
        public string Id { get; } = id;
        public string Name { get; } = name;
        public byte[] PollHash { get; } = pollHash;
        public DateTimeOffset ExpiresAt { get; } = expiresAt;
        public string? ApprovedAccountId { get; set; }
        public bool Rejected { get; set; }
        public bool Redeemed { get; set; }
    }

    private readonly object _gate = new();
    private readonly TimeProvider _clock = clock ?? TimeProvider.System;
    private readonly Dictionary<string, RequestState> _requests = new(StringComparer.Ordinal);
    private readonly Dictionary<string, (int Count, DateTimeOffset WindowStart)> _badAttempts = new(StringComparer.Ordinal);
    private OfferState? _offer;

    public PairingOffer CreateOffer(string address, int port)
    {
        if (!IsPrivateIpv4(address) || port is < 1 or > 65535)
            throw new ArgumentException("Invalid private LAN address or port");
        var secret = DeviceRegistry.RandomUrlSafe(32);
        lock (_gate)
        {
            _offer = new OfferState(SHA256.HashData(DeviceRegistry.DecodeUrlSafe(secret)),
                _clock.GetUtcNow().AddMinutes(2));
        }
        return new PairingOffer(1, address, port, CertificateStore.HostName, spkiSha256, secret);
    }

    public PairingRequestReceipt RequestPair(string secret, string deviceName, string? sourceAddress = null)
    {
        var source = sourceAddress ?? "unknown";
        lock (_gate)
        {
            EnsureNotLimited(source);
            if (_offer is null || _clock.GetUtcNow() >= _offer.ExpiresAt)
                throw new ExpiredOfferException();
            if (!MatchesSecret(secret, _offer.SecretHash))
            {
                RecordBadAttempt(source);
                throw new InvalidPairingException();
            }
            if (_requests.Values.Count(item => !item.Redeemed && !item.Rejected &&
                    _clock.GetUtcNow() < item.ExpiresAt) >= 8)
                throw new PairingRateLimitException();
            var name = SanitizeName(deviceName);
            _offer = null;
            var id = DeviceRegistry.RandomUrlSafe(16);
            var poll = DeviceRegistry.RandomUrlSafe(32);
            _requests[id] = new RequestState(id, name, SHA256.HashData(DeviceRegistry.DecodeUrlSafe(poll)),
                _clock.GetUtcNow().AddMinutes(2));
            return new PairingRequestReceipt(id, poll);
        }
    }

    public IReadOnlyList<PendingPairing> ListPending()
    {
        lock (_gate)
            return _requests.Values.Where(item => !item.Rejected && !item.Redeemed
                && item.ApprovedAccountId is null && _clock.GetUtcNow() < item.ExpiresAt)
                .Select(item => new PendingPairing(item.Id, item.Name, item.ExpiresAt)).ToArray();
    }

    public void Approve(string requestId, string accountId)
    {
        if (!MobileSession.IsValidUserId(accountId))
            throw new ArgumentException("Invalid account ID", nameof(accountId));
        lock (_gate)
        {
            var request = ValidRequest(requestId);
            if (request.Rejected || request.Redeemed) throw new InvalidPairingException();
            request.ApprovedAccountId = accountId;
        }
    }

    public void Reject(string requestId)
    {
        lock (_gate)
        {
            var request = ValidRequest(requestId);
            request.Rejected = true;
        }
    }

    public PairingRedeemResult Redeem(string requestId, string pollSecret, string? sourceAddress = null)
    {
        var source = sourceAddress ?? "unknown";
        lock (_gate)
        {
            EnsureNotLimited(source);
            var request = ValidRequest(requestId);
            if (!MatchesSecret(pollSecret, request.PollHash))
            {
                RecordBadAttempt(source);
                throw new InvalidPairingException();
            }
            if (request.Rejected) throw new RejectedPairingException();
            if (request.Redeemed) throw new InvalidPairingException();
            if (request.ApprovedAccountId is null)
                return new PairingRedeemResult("pending", null, null);
            var credential = devices.Issue(request.Name, request.ApprovedAccountId);
            request.Redeemed = true;
            return new PairingRedeemResult("approved", credential.DeviceId, credential.Token);
        }
    }

    public void Revoke(string deviceId) => devices.Revoke(deviceId);

    public void ResetPending()
    {
        lock (_gate)
        {
            _offer = null;
            _requests.Clear();
            _badAttempts.Clear();
        }
    }

    private RequestState ValidRequest(string id)
    {
        if (!_requests.TryGetValue(id, out var request) || _clock.GetUtcNow() >= request.ExpiresAt)
            throw new ExpiredOfferException();
        return request;
    }

    private void EnsureNotLimited(string source)
    {
        if (_badAttempts.TryGetValue(source, out var attempt)
            && _clock.GetUtcNow() - attempt.WindowStart < TimeSpan.FromMinutes(2)
            && attempt.Count >= 5)
            throw new PairingRateLimitException();
    }

    private void RecordBadAttempt(string source)
    {
        var now = _clock.GetUtcNow();
        if (!_badAttempts.TryGetValue(source, out var attempt)
            || now - attempt.WindowStart >= TimeSpan.FromMinutes(2))
            _badAttempts[source] = (1, now);
        else
            _badAttempts[source] = (attempt.Count + 1, attempt.WindowStart);
        EnsureNotLimited(source);
    }

    private static bool MatchesSecret(string? secret, byte[] hash) =>
        DeviceRegistry.TryDecodeUrlSafe(secret, out var bytes) && bytes.Length == 32
        && CryptographicOperations.FixedTimeEquals(SHA256.HashData(bytes), hash);

    private static string SanitizeName(string name)
    {
        var clean = new string((name ?? "").Where(c => !char.IsControl(c) && c is not '<' and not '>').ToArray())
            .Trim();
        if (clean.Length is < 1 or > 64) throw new ArgumentException("Invalid device name", nameof(name));
        return clean;
    }

    private static bool IsPrivateIpv4(string address)
    {
        if (!IPAddress.TryParse(address, out var ip) || ip.AddressFamily != System.Net.Sockets.AddressFamily.InterNetwork)
            return false;
        var bytes = ip.GetAddressBytes();
        return bytes[0] == 10 || bytes[0] == 172 && bytes[1] is >= 16 and <= 31
            || bytes[0] == 192 && bytes[1] == 168;
    }
}
