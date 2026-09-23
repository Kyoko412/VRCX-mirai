using System.Collections.Immutable;
using System.Text.RegularExpressions;

namespace VRCX.MobileCompanion.Session;

public sealed record SessionSnapshot(string AccountId, string TablePrefix,
    ImmutableHashSet<string> FriendIds, long Generation);

public sealed class MobileSession
{
    private static readonly Regex UserIdPattern = new(@"\Ausr_[A-Za-z0-9_-]{1,64}\z",
        RegexOptions.Compiled | RegexOptions.CultureInvariant);
    private readonly object _gate = new();
    private SessionSnapshot? _current;
    private long _generation;

    public void Open(string accountId, IReadOnlyCollection<string> friendIds)
    {
        if (!IsValidUserId(accountId))
            throw new ArgumentException("Invalid account ID", nameof(accountId));
        ArgumentNullException.ThrowIfNull(friendIds);
        var set = CopyValidFriends(friendIds);
        var prefix = accountId.Replace("-", "", StringComparison.Ordinal)
            .Replace("_", "", StringComparison.Ordinal);
        if (char.IsDigit(prefix[0]))
            prefix = "_" + prefix;

        lock (_gate)
            _current = new SessionSnapshot(accountId, prefix, set, ++_generation);
    }

    public void UpdateFriends(IReadOnlyCollection<string> friendIds)
    {
        ArgumentNullException.ThrowIfNull(friendIds);
        var set = CopyValidFriends(friendIds);
        lock (_gate)
        {
            if (_current is null)
                throw new InvalidOperationException("No active account session");
            _current = _current with { FriendIds = set, Generation = ++_generation };
        }
    }

    public void Close()
    {
        lock (_gate)
        {
            ++_generation;
            _current = null;
        }
    }

    public SessionSnapshot? Capture()
    {
        lock (_gate)
            return _current;
    }

    public bool IsCurrent(long generation)
    {
        lock (_gate)
            return _current is not null && _current.Generation == generation;
    }

    public static bool IsValidUserId(string? id) => id is not null && UserIdPattern.IsMatch(id);

    private static ImmutableHashSet<string> CopyValidFriends(IReadOnlyCollection<string> friendIds)
    {
        if (friendIds.Any(id => !IsValidUserId(id)))
            throw new ArgumentException("Invalid friend ID", nameof(friendIds));
        return friendIds.ToImmutableHashSet(StringComparer.Ordinal);
    }
}
