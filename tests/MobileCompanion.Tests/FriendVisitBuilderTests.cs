using VRCX.MobileCompanion.Data;

namespace VRCX.MobileCompanion.Tests;

public sealed class FriendVisitBuilderTests
{
    private static VisitEvent Event(long id, string at, string type, string location,
        string? previous = null, long? duration = null, string? name = null) =>
        new(id, at, type, location, name, previous, duration);

    [Fact]
    public void MoveAndOfflineCloseKnownVisits()
    {
        var visits = FriendVisitBuilder.Build([
            Event(1, "2026-09-01T10:00:00.000Z", "Online", "wrld_alpha:1", name: "Alpha"),
            Event(2, "2026-09-01T10:10:00.000Z", "GPS", "wrld_beta:2", "wrld_alpha:1", 600000, "Beta"),
            Event(3, "2026-09-01T10:45:00.000Z", "Offline", "wrld_beta:2")
        ]);

        Assert.Equal(2, visits.Count);
        Assert.Equal("2026-09-01T10:10:00.000Z", visits.Single(v => v.WorldId == "wrld_alpha").ExitedAt);
        Assert.Equal(2100000, visits.Single(v => v.WorldId == "wrld_beta").DurationMs);
    }

    [Fact]
    public void DuplicateGpsDoesNotCountButNewInstanceDoes()
    {
        var visits = FriendVisitBuilder.Build([
            Event(1, "2026-09-01T10:00:00.000Z", "Online", "wrld_alpha:1"),
            Event(2, "2026-09-01T10:01:00.000Z", "GPS", "wrld_alpha:1"),
            Event(3, "2026-09-01T10:15:00.000Z", "GPS", "wrld_alpha:2", "wrld_alpha:1", 900000)
        ]);

        Assert.Equal(2, visits.Count);
        Assert.All(visits, visit => Assert.Equal(2, visit.VisitCount));
        Assert.Null(visits.Single(v => v.Location == "wrld_alpha:2").ExitedAt);
    }

    [Fact]
    public void GapLeavesExitUnknownAndDurationToleranceUsesReportedStay()
    {
        var gap = FriendVisitBuilder.Build([
            Event(1, "2026-09-01T10:00:00.000Z", "Online", "wrld_alpha:1"),
            Event(2, "2026-09-01T12:00:00.000Z", "GPS", "wrld_beta:2", "wrld_alpha:1", 300000)
        ]);
        Assert.Null(gap.Single(v => v.WorldId == "wrld_alpha").ExitedAt);

        var near = FriendVisitBuilder.Build([
            Event(1, "2026-09-01T10:00:00.000Z", "Online", "wrld_alpha:1"),
            Event(2, "2026-09-01T10:32:00.000Z", "GPS", "wrld_beta:2", "wrld_alpha:1", 1800000)
        ]);
        Assert.Equal("2026-09-01T10:30:00.000Z", near.Single(v => v.WorldId == "wrld_alpha").ExitedAt);
    }

    [Fact]
    public void FirstGpsCanShowPreviousWorldWithoutInventedEntryOrExit()
    {
        var visits = FriendVisitBuilder.Build([
            Event(2, "2026-09-01T10:32:00.000Z", "GPS", "wrld_beta:2", "wrld_alpha:1", 1800000)
        ]);
        var previous = visits.Single(v => v.WorldId == "wrld_alpha");
        Assert.Null(previous.EnteredAt);
        Assert.Null(previous.ExitedAt);
        Assert.Equal(1800000, previous.DurationMs);
        Assert.NotEqual(previous.EventKey, visits.Single(v => v.WorldId == "wrld_beta").EventKey);
    }

    [Fact]
    public void EqualTimesHaveDeterministicUniqueKeys()
    {
        var visits = FriendVisitBuilder.Build([
            Event(2, "2026-09-01T10:00:00.000Z", "GPS", "wrld_beta:2", "wrld_alpha:1"),
            Event(1, "2026-09-01T10:00:00.000Z", "Online", "wrld_alpha:1")
        ]);
        Assert.Equal(2, visits.Select(v => v.EventKey).Distinct().Count());
        Assert.Equal("wrld_alpha", visits.Single(v => v.WorldId == "wrld_alpha").WorldId);
    }
}
