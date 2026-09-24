using VRCX.MobileCompanion.Session;

namespace VRCX.MobileCompanion.Tests;

public sealed class MobileSessionTests
{
    [Theory]
    [InlineData("")]
    [InlineData("abc")]
    [InlineData("usr_a;DROP TABLE x")]
    [InlineData("usr_ü")]
    public void RejectsInvalidAccountIds(string accountId)
    {
        var session = new MobileSession();
        Assert.Throws<ArgumentException>(() => session.Open(accountId, []));
        Assert.Null(session.Capture());
    }

    [Fact]
    public void CapturedFriendSetIsImmutableAndPrefixMatchesDesktop()
    {
        var friends = new List<string> { "usr_friend" };
        var session = new MobileSession();
        session.Open("usr_a-b_c", friends);
        var snapshot = session.Capture()!;
        friends.Clear();

        Assert.Equal("usrabc", snapshot.TablePrefix);
        Assert.Contains("usr_friend", snapshot.FriendIds);
        Assert.DoesNotContain("usr_other", snapshot.FriendIds);
        Assert.True(session.IsCurrent(snapshot.Generation));
    }

    [Fact]
    public void CloseAndAccountSwitchInvalidateInflightSnapshots()
    {
        var session = new MobileSession();
        session.Open("usr_a", ["usr_friend"]);
        var before = session.Capture()!;
        session.Close();
        Assert.False(session.IsCurrent(before.Generation));
        Assert.Null(session.Capture());

        session.Open("usr_b", []);
        Assert.Equal("usr_b", session.Capture()!.AccountId);
        Assert.False(session.IsCurrent(before.Generation));
    }

    [Fact]
    public void FriendUpdatesInvalidateInflightSnapshots()
    {
        var session = new MobileSession();
        session.Open("usr_a", ["usr_friend"]);
        var before = session.Capture()!;
        session.UpdateFriends([]);

        Assert.False(session.IsCurrent(before.Generation));
        Assert.DoesNotContain("usr_friend", session.Capture()!.FriendIds);
        Assert.Contains("usr_friend", before.FriendIds);
    }

    [Fact]
    public void LateFriendSyncCannotReplaceAnotherAccountsAllowlist()
    {
        var session = new MobileSession();
        session.Open("usr_a", ["usr_friend_a"]);
        session.Open("usr_b", ["usr_friend_b"]);
        var before = session.Capture()!;

        Assert.False(session.TryUpdateFriendsForAccount("usr_a", ["usr_friend_a"]));
        Assert.Equal(before, session.Capture());
        Assert.True(session.TryUpdateFriendsForAccount("usr_b", ["usr_friend_b2"]));
        Assert.Contains("usr_friend_b2", session.Capture()!.FriendIds);
    }
}
