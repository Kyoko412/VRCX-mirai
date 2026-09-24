using System;
using System.Collections.Generic;
using System.Linq;
using System.Net;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using VRCX.MobileCompanion.Contract;
using VRCX.MobileCompanion.Data;
using VRCX.MobileCompanion.Security;
using VRCX.MobileCompanion.Server;
using VRCX.MobileCompanion.Session;

namespace VRCX;

internal sealed class MobileCompanionBridge
{
    private const int ServicePort = 34682;
    private static readonly Lazy<MobileCompanionBridge> LazyInstance = new(() => new MobileCompanionBridge());
    public static MobileCompanionBridge Instance => LazyInstance.Value;

    private readonly MobileSession _session = new();
    private readonly DeviceRegistry _devices = new(new StorageDeviceStateStore());
    private readonly SemaphoreSlim _serviceGate = new(1, 1);
    private CertificateIdentity? _identity;
    private PairingCoordinator? _pairing;
    private CompanionHost? _host;
    private IPAddress? _address;

    private MobileCompanionBridge() { }

    public void Initialize() { /* Construction loads device metadata; listening remains off. */ }

    public string GetState()
    {
        var snapshot = _session.Capture();
        var addresses = PrivateAddressSelector.Available().Select(item => item.ToString()).ToArray();
        return Json(new
        {
            enabled = _host is not null,
            accountId = snapshot?.AccountId,
            availableAddresses = addresses,
            address = _address?.ToString(),
            port = _host?.BoundPort
        });
    }

    public async Task<string> Enable(string address)
    {
        await _serviceGate.WaitAsync();
        try
        {
            if (_session.Capture() is null) throw new InvalidOperationException("Log in before enabling phone access");
            if (_host is not null) return GetState();
            if (!IPAddress.TryParse(address, out var ip) || !PrivateAddressSelector.CanBind(ip))
                throw new ArgumentException("Choose an address on a Windows Private network", nameof(address));
            _identity ??= new CertificateStore().GetOrCreate();
            _pairing = new PairingCoordinator(_identity.SpkiSha256, _devices);
            var host = new CompanionHost(_session, new SqliteCompanionDb(), _pairing, _devices, _identity);
            await host.StartAsync(ip, ServicePort, CancellationToken.None);
            _host = host;
            _address = ip;
            return GetState();
        }
        finally { _serviceGate.Release(); }
    }

    public async Task<string> Disable()
    {
        await _serviceGate.WaitAsync();
        try
        {
            var host = _host;
            _host = null;
            _address = null;
            _pairing?.ResetPending();
            _pairing = null;
            if (host is not null) await host.StopAsync();
            return GetState();
        }
        finally { _serviceGate.Release(); }
    }

    public string CreateOffer()
    {
        if (_host is null || _address is null || _pairing is null || _session.Capture() is null)
            throw new InvalidOperationException("Phone access is not enabled");
        return Json(_pairing.CreateOffer(_address.ToString(), _host.BoundPort));
    }

    public string ListPending() => Json(_pairing?.ListPending() ?? []);

    public void Approve(string requestId)
    {
        var snapshot = _session.Capture() ?? throw new InvalidOperationException("No active account");
        (_pairing ?? throw new InvalidOperationException("Phone access is not enabled"))
            .Approve(requestId, snapshot.AccountId);
    }

    public void Reject(string requestId) =>
        (_pairing ?? throw new InvalidOperationException("Phone access is not enabled"))
            .Reject(requestId);

    public string ListDevices()
    {
        var accountId = _session.Capture()?.AccountId;
        return Json(_devices.ListDevices().Where(item => item.AccountId == accountId).ToArray());
    }

    public void Revoke(string deviceId)
    {
        var accountId = _session.Capture()?.AccountId;
        if (!_devices.ListDevices().Any(item => item.DeviceId == deviceId && item.AccountId == accountId))
            throw new ArgumentException("Unknown device", nameof(deviceId));
        _devices.Revoke(deviceId);
    }

    public void SetActiveAccount(string accountId)
    {
        _pairing?.ResetPending();
        _session.Open(accountId, []);
    }

    public void SetVerifiedFriends(string accountId, string[] friendIds)
    {
        _session.TryUpdateFriendsForAccount(accountId, friendIds ?? []);
    }

    public async Task ClearActiveAccount()
    {
        _session.Close();
        _pairing?.ResetPending();
        await Disable();
    }

    public void Exit()
    {
        _session.Close();
        Disable().GetAwaiter().GetResult();
    }

    private static string Json<T>(T value) => JsonSerializer.Serialize(value, CompanionJson.Options);

    private sealed class SqliteCompanionDb : ICompanionDb
    {
        public object[][] Query(string sql, IDictionary<string, object> args) => SQLite.Instance.Execute(sql, args);
    }

    private sealed class StorageDeviceStateStore : IDeviceStateStore
    {
        private const string StorageKey = "MobileCompanionDevicesV1";

        public IReadOnlyList<DeviceRecord> Load()
        {
            var value = VRCXStorage.Instance.Get(StorageKey);
            if (string.IsNullOrEmpty(value)) return [];
            try { return JsonSerializer.Deserialize<List<DeviceRecord>>(value, CompanionJson.Options) ?? []; }
            catch (JsonException) { return []; }
        }

        public void Save(IReadOnlyList<DeviceRecord> state) =>
            VRCXStorage.Instance.Set(StorageKey, Json(state));
    }
}

public partial class AppApiCef
{
    public string MobileCompanionGetState() => MobileCompanionBridge.Instance.GetState();
    public Task<string> MobileCompanionEnable(string address) => MobileCompanionBridge.Instance.Enable(address);
    public Task<string> MobileCompanionDisable() => MobileCompanionBridge.Instance.Disable();
    public string MobileCompanionCreateOffer() => MobileCompanionBridge.Instance.CreateOffer();
    public string MobileCompanionListPending() => MobileCompanionBridge.Instance.ListPending();
    public void MobileCompanionApprove(string requestId) => MobileCompanionBridge.Instance.Approve(requestId);
    public void MobileCompanionReject(string requestId) => MobileCompanionBridge.Instance.Reject(requestId);
    public string MobileCompanionListDevices() => MobileCompanionBridge.Instance.ListDevices();
    public void MobileCompanionRevoke(string deviceId) => MobileCompanionBridge.Instance.Revoke(deviceId);
    public void MobileCompanionSetActiveAccount(string accountId) => MobileCompanionBridge.Instance.SetActiveAccount(accountId);
    public void MobileCompanionSetVerifiedFriends(string accountId, string[] friendIds) =>
        MobileCompanionBridge.Instance.SetVerifiedFriends(accountId, friendIds);
    public Task MobileCompanionClearActiveAccount() => MobileCompanionBridge.Instance.ClearActiveAccount();
}
