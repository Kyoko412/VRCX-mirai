using System.Net;
using System.Net.Http.Headers;
using System.Security.Cryptography.X509Certificates;
using Microsoft.Data.Sqlite;
using VRCX.MobileCompanion.Data;
using VRCX.MobileCompanion.Security;
using VRCX.MobileCompanion.Server;
using VRCX.MobileCompanion.Session;

namespace VRCX.MobileCompanion.Tests;

public sealed class TwoAccountHttpFixtureTests
{
    [Fact]
    public async Task EveryDataRouteStaysInsideTheActiveAccountAndLegacyGameRowsStayHidden()
    {
        await using var fixture = await Fixture.CreateAsync();
        fixture.Authorize("usr_a");
        var routes = new[]
        {
            "/v1/friends",
            "/v1/friends/usr_friend/world-visits",
            "/v1/friends/usr_friend/encounters",
            "/v1/friends/usr_friend/bio-history",
            "/v1/me/game-log"
        };
        foreach (var route in routes)
        {
            var body = await fixture.GetBody(route);
            Assert.Contains("A_ONLY", body);
            Assert.DoesNotContain("B_ONLY", body);
            Assert.DoesNotContain("LEGACY_ONLY", body);
        }

        fixture.Session.Open("usr_b", ["usr_bfriend"]);
        Assert.Equal(HttpStatusCode.Forbidden,
            (await fixture.Client.GetAsync("/v1/friends")).StatusCode);
        fixture.Authorize("usr_b");
        foreach (var route in new[]
        {
            "/v1/friends", "/v1/friends/usr_bfriend/world-visits",
            "/v1/friends/usr_bfriend/encounters", "/v1/friends/usr_bfriend/bio-history",
            "/v1/me/game-log"
        })
        {
            var body = await fixture.GetBody(route);
            Assert.Contains("B_ONLY", body);
            Assert.DoesNotContain("A_ONLY", body);
            Assert.DoesNotContain("LEGACY_ONLY", body);
        }
        Assert.Equal(HttpStatusCode.NotFound,
            (await fixture.Client.GetAsync("/v1/friends/usr_friend/bio-history")).StatusCode);
    }

    private sealed class SqliteDb : ICompanionDb, IDisposable
    {
        private readonly SqliteConnection _connection = new("Data Source=:memory:");

        public SqliteDb()
        {
            _connection.Open();
            foreach (var prefix in new[] { "usra", "usrb" })
            {
                Execute($"CREATE TABLE {prefix}_friend_log_current (user_id TEXT, display_name TEXT)");
                Execute($"CREATE TABLE {prefix}_feed_gps (id INTEGER, created_at TEXT, user_id TEXT, location TEXT, world_name TEXT, previous_location TEXT, time INTEGER)");
                Execute($"CREATE TABLE {prefix}_feed_online_offline (id INTEGER, created_at TEXT, user_id TEXT, type TEXT, location TEXT, world_name TEXT, time INTEGER)");
                Execute($"CREATE TABLE {prefix}_feed_bio (id INTEGER, created_at TEXT, user_id TEXT, bio TEXT, previous_bio TEXT)");
                Execute($"CREATE TABLE {prefix}_mutual_encounters_v1 (visit_key TEXT, other_user_id TEXT, location TEXT, observed_at TEXT, world_name_snapshot TEXT, status TEXT)");
            }
            Execute("CREATE TABLE gamelog_location (id INTEGER, created_at TEXT, location TEXT, world_id TEXT, world_name TEXT, time INTEGER)");
            Execute("CREATE TABLE mobile_game_location_owner_v1 (gamelog_location_id INTEGER, owner_user_id TEXT)");
            foreach (var (prefix, friend, marker, id) in new[]
            {
                ("usra", "usr_friend", "A_ONLY", 1),
                ("usrb", "usr_bfriend", "B_ONLY", 2)
            })
            {
                Execute($"INSERT INTO {prefix}_friend_log_current VALUES ('{friend}', '{marker}')");
                Execute($"INSERT INTO {prefix}_feed_gps VALUES ({id}, '2026-09-24T02:00:00Z', '{friend}', 'wrld_{id}:instance', '{marker}', NULL, NULL)");
                Execute($"INSERT INTO {prefix}_feed_bio VALUES ({id}, '2026-09-24T02:00:00Z', '{friend}', '{marker}', 'old')");
                Execute($"INSERT INTO {prefix}_mutual_encounters_v1 VALUES ('visit:{id}', '{friend}', 'wrld_{id}:instance', '2026-09-24T02:00:00Z', '{marker}', 'qualified')");
                Execute($"INSERT INTO gamelog_location VALUES ({id}, '2026-09-24T02:00:00Z', 'wrld_{id}:instance', 'wrld_{id}', '{marker}', NULL)");
                Execute($"INSERT INTO mobile_game_location_owner_v1 VALUES ({id}, 'usr_{(id == 1 ? "a" : "b")}')");
            }
            Execute("INSERT INTO gamelog_location VALUES (3, '2026-09-24T02:00:00Z', 'wrld_3:instance', 'wrld_3', 'LEGACY_ONLY', NULL)");
        }

        private void Execute(string sql)
        {
            using var command = new SqliteCommand(sql, _connection);
            command.ExecuteNonQuery();
        }

        public object[][] Query(string sql, IDictionary<string, object> args)
        {
            using var command = new SqliteCommand(sql, _connection);
            foreach (var (name, value) in args)
                command.Parameters.AddWithValue(name, value);
            using var reader = command.ExecuteReader();
            var rows = new List<object[]>();
            while (reader.Read())
            {
                var row = new object[reader.FieldCount];
                reader.GetValues(row);
                rows.Add(row);
            }
            return rows.ToArray();
        }

        public void Dispose() => _connection.Dispose();
    }

    private sealed class MemoryStore : IDeviceStateStore
    {
        private IReadOnlyList<DeviceRecord> _devices = [];
        public IReadOnlyList<DeviceRecord> Load() => _devices;
        public void Save(IReadOnlyList<DeviceRecord> state) => _devices = state.ToArray();
    }

    private sealed class MemoryCertificateStore : ICertificatePersistence
    {
        private X509Certificate2? _certificate;
        public X509Certificate2? Find() => _certificate;
        public void Save(X509Certificate2 certificate) => _certificate = certificate;
    }

    private sealed class Fixture : IAsyncDisposable
    {
        public MobileSession Session { get; } = new();
        public DeviceRegistry Devices { get; } = new(new MemoryStore());
        public SqliteDb Db { get; } = new();
        public CompanionHost Host { get; private set; } = null!;
        public HttpClient Client { get; private set; } = null!;

        public static async Task<Fixture> CreateAsync()
        {
            var fixture = new Fixture();
            fixture.Session.Open("usr_a", ["usr_friend"]);
            var identity = new CertificateStore(new MemoryCertificateStore()).GetOrCreate();
            var pairing = new PairingCoordinator(identity.SpkiSha256, fixture.Devices);
            fixture.Host = new CompanionHost(fixture.Session, fixture.Db, pairing,
                fixture.Devices, identity, _ => true);
            await fixture.Host.StartAsync(IPAddress.Loopback, 0, CancellationToken.None);
            fixture.Client = new HttpClient(new HttpClientHandler
            {
                UseProxy = false,
                ServerCertificateCustomValidationCallback = (_, certificate, _, _) =>
                    certificate is not null && certificate.RawData.SequenceEqual(identity.Certificate.RawData)
            }) { BaseAddress = new Uri($"https://127.0.0.1:{fixture.Host.BoundPort}") };
            return fixture;
        }

        public void Authorize(string accountId)
        {
            var credential = Devices.Issue("Phone", accountId);
            Client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", credential.Token);
        }

        public async Task<string> GetBody(string route)
        {
            using var response = await Client.GetAsync(route);
            Assert.Equal(HttpStatusCode.OK, response.StatusCode);
            return await response.Content.ReadAsStringAsync();
        }

        public async ValueTask DisposeAsync()
        {
            Client.Dispose();
            await Host.StopAsync();
            Db.Dispose();
        }
    }
}
