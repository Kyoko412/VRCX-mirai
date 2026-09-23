using VRCX.MobileCompanion.Data;
using VRCX.MobileCompanion.Session;

namespace VRCX.MobileCompanion.Tests;

public sealed class GameLocationRepositoryTests
{
    [Fact]
    public void LegacyGlobalRowsWithoutOwnerMarkerAreNeverReturned()
    {
        var db = new FakeDb((sql, args) =>
        {
            Assert.Contains("JOIN mobile_game_location_owner_v1", sql);
            Assert.Contains("owner_user_id = @accountId", sql);
            Assert.Equal("usr_me", args["@accountId"]);
            return [];
        });
        var session = new MobileSession();
        session.Open("usr_me", []);

        var page = new GameLocationRepository(db).GetGameLog(session.Capture()!, null, 20);
        Assert.Empty(page.Items);
        Assert.Null(page.NextCursor);
    }

    [Fact]
    public void PaginatesSameTimestampByLocationRowId()
    {
        const string at = "2026-09-24T02:00:00Z";
        var db = new FakeDb((_, args) =>
        {
            var before = args.TryGetValue("@id", out var id) ? Convert.ToInt64(id) : long.MaxValue;
            return Enumerable.Range(1, 51).Reverse().Where(i => i < before)
                .Take(Convert.ToInt32(args["@take"]))
                .Select(i => new object[] { (long)i, at, $"wrld_a:{i}", "wrld_a", "World A", DBNull.Value })
                .ToArray();
        });
        var session = new MobileSession();
        session.Open("usr_me", []);
        var repo = new GameLocationRepository(db);
        var first = repo.GetGameLog(session.Capture()!, null, 50);
        var second = repo.GetGameLog(session.Capture()!, first.NextCursor, 50);
        Assert.Equal(51, first.Items.Concat(second.Items).Select(item => item.Id).Distinct().Count());
        Assert.Contains("created_at = @at AND g.id < @id", db.ExecutedSql);
    }

    private sealed class FakeDb(Func<string, IDictionary<string, object>, object[][]> query) : ICompanionDb
    {
        public string ExecutedSql { get; private set; } = "";
        public object[][] Query(string sql, IDictionary<string, object> args)
        {
            ExecutedSql += sql;
            return query(sql, args);
        }
    }
}
