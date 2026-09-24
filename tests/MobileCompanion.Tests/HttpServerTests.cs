using System.Net;
using System.Net.Http.Headers;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using System.Text.Json;
using VRCX.MobileCompanion.Data;
using VRCX.MobileCompanion.Security;
using VRCX.MobileCompanion.Server;
using VRCX.MobileCompanion.Session;

namespace VRCX.MobileCompanion.Tests;

public sealed class HttpServerTests
{
    [Fact]
    public async Task DataRequiresBearerOverHttpsAndServiceIsOptIn()
    {
        await using var fixture = await Fixture.CreateAsync();
        Assert.True(fixture.Host.IsRunning);
        Assert.True(fixture.Host.BoundPort > 0);
        Assert.Equal(HttpStatusCode.Unauthorized, (await fixture.Client.GetAsync("/v1/status")).StatusCode);
        fixture.Authorize();
        using var response = await fixture.Client.GetAsync("/v1/status");
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Contains("usr_me", await response.Content.ReadAsStringAsync());
        Assert.Equal(HttpStatusCode.NotFound, (await fixture.Client.GetAsync("/v1/sql")).StatusCode);
    }

    [Fact]
    public async Task RevokedAndWrongAccountTokensCannotReadData()
    {
        await using var fixture = await Fixture.CreateAsync();
        var wrong = fixture.Devices.Issue("Other account", "usr_other");
        fixture.Client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", wrong.Token);
        var wrongAccount = await fixture.Client.GetAsync("/v1/friends");
        Assert.Equal(HttpStatusCode.Forbidden, wrongAccount.StatusCode);
        Assert.Contains("account_changed", await wrongAccount.Content.ReadAsStringAsync());
        var current = fixture.Authorize();
        fixture.Devices.Revoke(current.DeviceId);
        var revoked = await fixture.Client.GetAsync("/v1/friends");
        Assert.Equal(HttpStatusCode.Forbidden, revoked.StatusCode);
        Assert.Contains("forbidden", await revoked.Content.ReadAsStringAsync());
    }

    [Fact]
    public async Task FriendGuardValidationAndDatabaseFailureMapToSafeErrors()
    {
        await using var fixture = await Fixture.CreateAsync();
        fixture.Authorize();
        Assert.Equal(HttpStatusCode.NotFound,
            (await fixture.Client.GetAsync("/v1/friends/usr_stranger/bio-history")).StatusCode);
        Assert.Equal(HttpStatusCode.BadRequest, (await fixture.Client.GetAsync("/v1/friends?limit=51")).StatusCode);
        Assert.Equal(HttpStatusCode.BadRequest,
            (await fixture.Client.GetAsync("/v1/friends/usr_friend/bio-history?cursor=bad! ")).StatusCode);
        fixture.Db.Throw = true;
        var response = await fixture.Client.GetAsync("/v1/friends");
        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        var body = await response.Content.ReadAsStringAsync();
        Assert.DoesNotContain("secret database detail", body);
        Assert.Contains("unavailable", body);
    }

    [Fact]
    public async Task LogoutDuringReadReturnsConflictWithoutPreviousAccountData()
    {
        await using var fixture = await Fixture.CreateAsync();
        fixture.Authorize();
        fixture.Db.Block = true;
        var pending = fixture.Client.GetAsync("/v1/friends");
        await fixture.Db.ReadStarted.Task.WaitAsync(TimeSpan.FromSeconds(5));
        fixture.Session.Close();
        fixture.Db.ReleaseRead.Set();
        var response = await pending;
        Assert.Equal(HttpStatusCode.Conflict, response.StatusCode);
        Assert.DoesNotContain("Alice", await response.Content.ReadAsStringAsync());
    }

    [Fact]
    public async Task RevocationDuringReadCannotReturnRows()
    {
        await using var fixture = await Fixture.CreateAsync();
        var credential = fixture.Authorize();
        fixture.Db.Block = true;
        var pending = fixture.Client.GetAsync("/v1/friends");
        await fixture.Db.ReadStarted.Task.WaitAsync(TimeSpan.FromSeconds(5));
        fixture.Devices.Revoke(credential.DeviceId);
        fixture.Db.ReleaseRead.Set();
        var response = await pending;
        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
        Assert.DoesNotContain("Alice", await response.Content.ReadAsStringAsync());
    }

    [Fact]
    public async Task PairingRoutesReturnNoAccountDataBeforeDesktopApproval()
    {
        await using var fixture = await Fixture.CreateAsync();
        var offer = fixture.Pairing.CreateOffer("192.168.1.10", fixture.Host.BoundPort);
        var request = await fixture.Client.PostAsync("/v1/pair/requests", Json($"{{\"secret\":\"{offer.Secret}\",\"deviceName\":\"Phone\"}}"));
        Assert.Equal(HttpStatusCode.Created, request.StatusCode);
        var body = await request.Content.ReadAsStringAsync();
        Assert.DoesNotContain("usr_me", body);
        using var parsed = JsonDocument.Parse(body);
        var requestId = parsed.RootElement.GetProperty("requestId").GetString();
        var poll = parsed.RootElement.GetProperty("pollSecret").GetString();
        var redeem = Json($"{{\"requestId\":\"{requestId}\",\"pollSecret\":\"{poll}\"}}");
        Assert.Equal(HttpStatusCode.Accepted,
            (await fixture.Client.PostAsync("/v1/pair/redeem", redeem)).StatusCode);
        fixture.Pairing.Approve(requestId!, "usr_me");
        var approved = await fixture.Client.PostAsync("/v1/pair/redeem", Json($"{{\"requestId\":\"{requestId}\",\"pollSecret\":\"{poll}\"}}"));
        Assert.Equal(HttpStatusCode.OK, approved.StatusCode);
        Assert.DoesNotContain("usr_me", await approved.Content.ReadAsStringAsync());
    }

    [Fact]
    public void ProductionSelectorRejectsLoopbackAndPublicIpv4()
    {
        Assert.False(PrivateAddressSelector.IsPrivateIpv4(IPAddress.Loopback));
        Assert.False(PrivateAddressSelector.IsPrivateIpv4(IPAddress.Parse("8.8.8.8")));
        Assert.True(PrivateAddressSelector.IsPrivateIpv4(IPAddress.Parse("192.168.1.10")));
    }

    private static StringContent Json(string value) => new(value, Encoding.UTF8, "application/json");

    private sealed class FakeDb : ICompanionDb
    {
        public bool Throw { get; set; }
        public bool Block { get; set; }
        public TaskCompletionSource ReadStarted { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);
        public ManualResetEventSlim ReleaseRead { get; } = new(false);
        public object[][] Query(string sql, IDictionary<string, object> args)
        {
            if (Block)
            {
                ReadStarted.TrySetResult();
                if (!ReleaseRead.Wait(TimeSpan.FromSeconds(5))) throw new TimeoutException();
            }
            if (Throw) throw new InvalidOperationException("secret database detail");
            return sql.Contains("friend_log_current", StringComparison.Ordinal)
                ? [["usr_friend", "Alice"]] : [];
        }
    }

    private sealed class MemoryStateStore : IDeviceStateStore
    {
        private IReadOnlyList<DeviceRecord> _state = [];
        public IReadOnlyList<DeviceRecord> Load() => _state;
        public void Save(IReadOnlyList<DeviceRecord> state) => _state = state.ToArray();
    }

    private sealed class MemoryCertStore : ICertificatePersistence
    {
        private X509Certificate2? _certificate;
        public X509Certificate2? Find() => _certificate;
        public void Save(X509Certificate2 certificate) => _certificate = certificate;
    }

    private sealed class Fixture : IAsyncDisposable
    {
        public MobileSession Session { get; } = new();
        public FakeDb Db { get; } = new();
        public DeviceRegistry Devices { get; } = new(new MemoryStateStore());
        public PairingCoordinator Pairing { get; private set; } = null!;
        public CompanionHost Host { get; private set; } = null!;
        public HttpClient Client { get; private set; } = null!;

        public static async Task<Fixture> CreateAsync()
        {
            var fixture = new Fixture();
            fixture.Session.Open("usr_me", ["usr_friend"]);
            var identity = new CertificateStore(new MemoryCertStore()).GetOrCreate();
            fixture.Pairing = new PairingCoordinator(identity.SpkiSha256, fixture.Devices);
            fixture.Host = new CompanionHost(fixture.Session, fixture.Db, fixture.Pairing,
                fixture.Devices, identity, _ => true);
            Assert.False(fixture.Host.IsRunning);
            await fixture.Host.StartAsync(IPAddress.Loopback, 0, CancellationToken.None);
            var handler = new HttpClientHandler
            {
                UseProxy = false,
                ServerCertificateCustomValidationCallback = (_, cert, _, _) => cert is not null &&
                    cert.RawData.SequenceEqual(identity.Certificate.RawData)
            };
            fixture.Client = new HttpClient(handler)
            {
                BaseAddress = new Uri($"https://127.0.0.1:{fixture.Host.BoundPort}")
            };
            return fixture;
        }

        public DeviceCredential Authorize()
        {
            var credential = Devices.Issue("Phone", "usr_me");
            Client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", credential.Token);
            return credential;
        }

        public async ValueTask DisposeAsync()
        {
            Db.ReleaseRead.Set();
            Client.Dispose();
            await Host.StopAsync();
        }
    }
}
