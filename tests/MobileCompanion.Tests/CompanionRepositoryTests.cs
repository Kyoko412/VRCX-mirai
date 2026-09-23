using VRCX.MobileCompanion.Data;
using VRCX.MobileCompanion.Session;

namespace VRCX.MobileCompanion.Tests;

public sealed class CompanionRepositoryTests
{
    [Fact]
    public void FriendListIntersectsVerifiedSetAndSearchesLiteralApostrophe()
    {
        var db = new FakeDb((_, _) =>
        [
            ["usr_a", "Alice's World"],
            ["usr_old", "Alice's Old Friend"],
            ["usr_b", "Bob"]
        ]);
        var session = new MobileSession();
        session.Open("usr_me", ["usr_a", "usr_b"]);
        var page = new CompanionRepository(db).ListFriends(session.Capture()!, "Alice's", null, 20);

        Assert.Equal("usr_a", Assert.Single(page.Items).Id);
        Assert.Contains("usrme_friend_log_current", db.ExecutedSql);
        Assert.DoesNotContain("Alice's", db.ExecutedSql);
    }

    [Fact]
    public void RemovedFriendIsRejectedBeforeQuery()
    {
        var db = new FakeDb((_, _) => []);
        var session = new MobileSession();
        session.Open("usr_me", ["usr_friend"]);
        session.UpdateFriends([]);
        var repo = new CompanionRepository(db);

        Assert.Throws<FriendNotFoundException>(() =>
            repo.GetBioHistory(session.Capture()!, "usr_friend", null, 20));
        Assert.Equal(0, db.CallCount);
    }

    [Fact]
    public void BioPaginationPreservesEqualTimestampsAndAccountBoundary()
    {
        const string at = "2026-09-24T02:00:00Z";
        var db = new FakeDb((sql, args) =>
        {
            Assert.Contains("usrme_feed_bio", sql);
            Assert.DoesNotContain("usrother_", sql);
            var before = args.TryGetValue("@id", out var id) ? Convert.ToInt64(id) : long.MaxValue;
            return Enumerable.Range(1, 51).Reverse().Where(i => i < before)
                .Take(Convert.ToInt32(args["@take"]))
                .Select(i => new object[] { at, (long)i, "new", "old" }).ToArray();
        });
        var session = new MobileSession();
        session.Open("usr_me", ["usr_friend"]);
        var repo = new CompanionRepository(db);
        var first = repo.GetBioHistory(session.Capture()!, "usr_friend", null, 50);
        var second = repo.GetBioHistory(session.Capture()!, "usr_friend", first.NextCursor, 50);

        Assert.Equal(50, first.Items.Count);
        Assert.Single(second.Items);
        Assert.Equal(51, first.Items.Concat(second.Items).Select(x => x.Id).Distinct().Count());
        Assert.Null(second.NextCursor);
        Assert.Contains("created_at = @at AND id < @id", db.ExecutedSql);
    }

    [Fact]
    public void EncounterCountsKeepUnknownOutOfQualifiedItems()
    {
        var db = new FakeDb((sql, _) => sql.Contains("SUM(CASE", StringComparison.Ordinal)
            ? [[2L, 1L]]
            : [["visit:2", "2026-09-24T02:00:00Z", "wrld_a:123", "World A"]]);
        var session = new MobileSession();
        session.Open("usr_me", ["usr_friend"]);
        var page = new CompanionRepository(db).GetEncounters(session.Capture()!, "usr_friend", null, 20);

        Assert.Equal(2, page.QualifiedCount);
        Assert.Equal(1, page.UnknownCount);
        Assert.Single(page.Items);
        Assert.Contains("status = 'qualified'", db.ExecutedSql);
    }

    [Fact]
    public void EncounterPagesKeepRowsWithSameObservationTime()
    {
        const string at = "2026-09-24T02:00:00Z";
        var db = new FakeDb((sql, args) =>
        {
            if (sql.Contains("SUM(CASE", StringComparison.Ordinal)) return [[51L, 0L]];
            var after = args.TryGetValue("@key", out var key) ? Convert.ToString(key) : null;
            return Enumerable.Range(1, 51).Reverse().Select(i => $"visit:{i:D3}")
                .Where(key => after is null || string.CompareOrdinal(key, after) < 0)
                .Take(Convert.ToInt32(args["@take"]))
                .Select(key => new object[] { key, at, "wrld_a:123", "World A" }).ToArray();
        });
        var session = new MobileSession();
        session.Open("usr_me", ["usr_friend"]);
        var repo = new CompanionRepository(db);

        var first = repo.GetEncounters(session.Capture()!, "usr_friend", null, 50);
        var second = repo.GetEncounters(session.Capture()!, "usr_friend", first.NextCursor, 50);
        Assert.Equal(51, first.Items.Concat(second.Items).Select(item => item.VisitKey).Distinct().Count());
        Assert.Null(second.NextCursor);
        Assert.Contains("observed_at = @at AND visit_key < @key", db.ExecutedSql);
    }

    [Fact]
    public void EmptyBioHistoryHasNoCursor()
    {
        var db = new FakeDb((_, _) => []);
        var session = new MobileSession();
        session.Open("usr_me", ["usr_friend"]);
        var page = new CompanionRepository(db).GetBioHistory(session.Capture()!, "usr_friend", null, 20);
        Assert.Empty(page.Items);
        Assert.Null(page.NextCursor);
    }

    [Fact]
    public void WorldVisitsPageAllEqualTimestampEventsWithoutDuplicates()
    {
        const string at = "2026-09-24T02:00:00Z";
        var db = new FakeDb((sql, args) =>
        {
            Assert.Contains("usrme_feed_gps", sql);
            Assert.Contains("usrme_feed_online_offline", sql);
            Assert.DoesNotContain("@dateFrom", sql);
            Assert.Equal("usr_friend", args["@friendId"]);
            return Enumerable.Range(1, 101).Select(i =>
                new object[] { (long)i, at, "Online", $"wrld_a:{i}", "World A", DBNull.Value, DBNull.Value }).ToArray();
        });
        var session = new MobileSession();
        session.Open("usr_me", ["usr_friend"]);
        var repo = new CompanionRepository(db);
        var first = repo.GetWorldVisits(session.Capture()!, "usr_friend", null, 50);
        var second = repo.GetWorldVisits(session.Capture()!, "usr_friend", first.NextCursor, 50);
        var third = repo.GetWorldVisits(session.Capture()!, "usr_friend", second.NextCursor, 50);

        var all = first.Items.Concat(second.Items).Concat(third.Items).ToArray();
        Assert.Equal(101, all.Length);
        Assert.Equal(101, all.Select(item => item.EventKey).Distinct().Count());
        Assert.All(all, item => Assert.Equal(101, item.VisitCount));
        Assert.Null(third.NextCursor);
    }

    [Fact]
    public void InvalidCursorAndLimitAreRejectedBeforeDatabaseAccess()
    {
        var db = new FakeDb((_, _) => []);
        var session = new MobileSession();
        session.Open("usr_me", ["usr_friend"]);
        var repo = new CompanionRepository(db);
        Assert.Throws<ArgumentException>(() => repo.GetBioHistory(session.Capture()!, "usr_friend", "bad!", 20));
        Assert.Throws<ArgumentOutOfRangeException>(() => repo.ListFriends(session.Capture()!, null, null, 51));
        Assert.Equal(0, db.CallCount);
    }

    private sealed class FakeDb(Func<string, IDictionary<string, object>, object[][]> query) : ICompanionDb
    {
        public int CallCount { get; private set; }
        public string ExecutedSql { get; private set; } = "";

        public object[][] Query(string sql, IDictionary<string, object> args)
        {
            CallCount++;
            ExecutedSql += sql;
            return query(sql, args);
        }
    }
}
