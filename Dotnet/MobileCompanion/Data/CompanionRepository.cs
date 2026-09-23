using System.Globalization;
using VRCX.MobileCompanion.Contract;
using VRCX.MobileCompanion.Session;

namespace VRCX.MobileCompanion.Data;

public sealed class FriendNotFoundException : Exception
{
    public FriendNotFoundException() : base("Friend is not in the current verified list") { }
}

public sealed class CompanionRepository(ICompanionDb db)
{
    public Page<FriendDto> ListFriends(SessionSnapshot snapshot, string? search, string? cursor, int limit)
    {
        ValidateLimit(limit);
        if (search is { Length: > 100 }) throw new ArgumentException("Search is too long", nameof(search));
        var after = KeysetCursor.Decode(cursor);
        var table = Table(snapshot, "friend_log_current");
        var rows = db.Query($"SELECT user_id, display_name FROM {table}", new Dictionary<string, object>());
        var friends = rows
            .Select(row => new FriendDto(AsString(row[0]), AsString(row[1])))
            .Where(item => snapshot.FriendIds.Contains(item.Id)
                && (string.IsNullOrEmpty(search) || item.DisplayName.Contains(search, StringComparison.OrdinalIgnoreCase)))
            .OrderBy(item => item.DisplayName, StringComparer.OrdinalIgnoreCase)
            .ThenBy(item => item.Id, StringComparer.Ordinal)
            .Where(item => after is null || StringComparer.OrdinalIgnoreCase.Compare(item.DisplayName, after.At) > 0
                || (StringComparer.OrdinalIgnoreCase.Compare(item.DisplayName, after.At) == 0
                    && StringComparer.Ordinal.Compare(item.Id, after.Key) > 0))
            .Take(limit + 1).ToArray();
        var items = friends.Take(limit).ToArray();
        var last = items.LastOrDefault();
        return new Page<FriendDto>(snapshot.AccountId, items,
            friends.Length > limit && last is not null ? new KeysetCursor(last.DisplayName, last.Id).Encode() : null);
    }

    public Page<BioChangeDto> GetBioHistory(SessionSnapshot snapshot, string friendId, string? cursor, int limit)
    {
        ValidateLimit(limit);
        EnsureFriend(snapshot, friendId);
        var after = KeysetCursor.Decode(cursor);
        var args = new Dictionary<string, object> { ["@friendId"] = friendId, ["@take"] = limit + 1 };
        var where = "";
        if (after is not null)
        {
            if (!long.TryParse(after.Key, NumberStyles.None, CultureInfo.InvariantCulture, out var id) || id <= 0)
                throw new ArgumentException("Invalid cursor", nameof(cursor));
            args["@at"] = after.At;
            args["@id"] = id;
            where = "AND (created_at < @at OR (created_at = @at AND id < @id))";
        }
        var rows = db.Query($"SELECT created_at, id, bio, previous_bio FROM {Table(snapshot, "feed_bio")} " +
            "WHERE user_id = @friendId AND bio IS NOT NULL AND previous_bio IS NOT NULL AND bio <> previous_bio " +
            $"{where} ORDER BY created_at DESC, id DESC LIMIT @take", args);
        var items = rows.Take(limit).Select(row => new BioChangeDto(AsInt64(row[1]), AsNullableString(row[3]),
            AsNullableString(row[2]), AsString(row[0]))).ToArray();
        var last = items.LastOrDefault();
        return new Page<BioChangeDto>(snapshot.AccountId, items,
            rows.Length > limit && last is not null
                ? new KeysetCursor(last.ObservedAt, last.Id.ToString(CultureInfo.InvariantCulture)).Encode() : null);
    }

    public EncounterPage GetEncounters(SessionSnapshot snapshot, string friendId, string? cursor, int limit)
    {
        ValidateLimit(limit);
        EnsureFriend(snapshot, friendId);
        var after = KeysetCursor.Decode(cursor);
        var table = Table(snapshot, "mutual_encounters_v1");
        var args = new Dictionary<string, object> { ["@friendId"] = friendId, ["@take"] = limit + 1 };
        var where = "";
        if (after is not null)
        {
            args["@at"] = after.At;
            args["@key"] = after.Key;
            where = "AND (observed_at < @at OR (observed_at = @at AND visit_key < @key))";
        }
        var counts = db.Query($"SELECT SUM(CASE WHEN status = 'qualified' THEN 1 ELSE 0 END), " +
            $"SUM(CASE WHEN status = 'unknown' THEN 1 ELSE 0 END) FROM {table} WHERE other_user_id = @friendId",
            new Dictionary<string, object> { ["@friendId"] = friendId });
        var rows = db.Query($"SELECT visit_key, observed_at, location, world_name_snapshot FROM {table} " +
            $"WHERE other_user_id = @friendId AND status = 'qualified' {where} " +
            "ORDER BY observed_at DESC, visit_key DESC LIMIT @take", args);
        var items = rows.Take(limit).Select(row =>
        {
            var location = AsNullableString(row[2]);
            var worldId = location?.Split(':', 2)[0];
            return new EncounterDto(AsString(row[0]), worldId, AsNullableString(row[3]), location, AsString(row[1]));
        }).ToArray();
        var last = items.LastOrDefault();
        return new EncounterPage(snapshot.AccountId,
            counts.Length == 0 ? 0 : (int)AsInt64(counts[0][0]),
            counts.Length == 0 ? 0 : (int)AsInt64(counts[0][1]), items,
            rows.Length > limit && last is not null ? new KeysetCursor(last.ObservedAt, last.VisitKey).Encode() : null);
    }

    internal static void ValidateLimit(int limit)
    {
        if (limit is < 1 or > 50) throw new ArgumentOutOfRangeException(nameof(limit));
    }

    internal static void EnsureFriend(SessionSnapshot snapshot, string friendId)
    {
        if (!MobileSession.IsValidUserId(friendId) || !snapshot.FriendIds.Contains(friendId))
            throw new FriendNotFoundException();
    }

    internal static string Table(SessionSnapshot snapshot, string suffix)
    {
        if (!MobileSession.IsValidUserId(snapshot.AccountId))
            throw new ArgumentException("Invalid account snapshot", nameof(snapshot));
        var expected = snapshot.AccountId.Replace("-", "", StringComparison.Ordinal)
            .Replace("_", "", StringComparison.Ordinal);
        if (snapshot.TablePrefix != expected)
            throw new ArgumentException("Invalid account snapshot", nameof(snapshot));
        return $"{expected}_{suffix}";
    }

    internal static string AsString(object? value) => Convert.ToString(value, CultureInfo.InvariantCulture) ?? "";
    internal static string? AsNullableString(object? value) => value is null or DBNull ? null : AsString(value);
    internal static long AsInt64(object? value) => value is null or DBNull ? 0 : Convert.ToInt64(value, CultureInfo.InvariantCulture);
}
