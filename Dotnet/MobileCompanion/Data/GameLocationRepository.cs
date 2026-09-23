using System.Globalization;
using VRCX.MobileCompanion.Contract;
using VRCX.MobileCompanion.Session;

namespace VRCX.MobileCompanion.Data;

public sealed class GameLocationRepository(ICompanionDb db)
{
    public Page<GameLocationDto> GetGameLog(SessionSnapshot snapshot, string? cursor, int limit)
    {
        CompanionRepository.ValidateLimit(limit);
        var after = KeysetCursor.Decode(cursor);
        var args = new Dictionary<string, object>
        {
            ["@accountId"] = snapshot.AccountId,
            ["@take"] = limit + 1
        };
        var where = "";
        if (after is not null)
        {
            if (!long.TryParse(after.Key, NumberStyles.None, CultureInfo.InvariantCulture, out var id) || id <= 0)
                throw new ArgumentException("Invalid cursor", nameof(cursor));
            args["@at"] = after.At;
            args["@id"] = id;
            where = "AND (g.created_at < @at OR (g.created_at = @at AND g.id < @id))";
        }
        // Legacy global rows have no owner marker and therefore cannot pass this join.
        var rows = db.Query("SELECT g.id, g.created_at, g.location, g.world_id, g.world_name, g.time " +
            "FROM gamelog_location g JOIN mobile_game_location_owner_v1 o ON o.gamelog_location_id = g.id " +
            $"WHERE o.owner_user_id = @accountId {where} ORDER BY g.created_at DESC, g.id DESC LIMIT @take", args);
        var items = rows.Take(limit).Select(row => new GameLocationDto(
            CompanionRepository.AsInt64(row[0]), CompanionRepository.AsString(row[1]),
            CompanionRepository.AsString(row[2]), CompanionRepository.AsNullableString(row[3]),
            CompanionRepository.AsNullableString(row[4]),
            row[5] is null or DBNull ? null : CompanionRepository.AsInt64(row[5]))).ToArray();
        var last = items.LastOrDefault();
        return new Page<GameLocationDto>(snapshot.AccountId, items,
            rows.Length > limit && last is not null
                ? new KeysetCursor(last.CreatedAt, last.Id.ToString(CultureInfo.InvariantCulture)).Encode()
                : null);
    }
}
