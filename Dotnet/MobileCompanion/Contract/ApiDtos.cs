using System.Text.Json;

namespace VRCX.MobileCompanion.Contract;

public static class CompanionJson
{
    public static readonly JsonSerializerOptions Options = new(JsonSerializerDefaults.Web)
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        PropertyNameCaseInsensitive = true
    };
}

public sealed record ApiError(string Code, string Message);
public sealed record Page<T>(string AccountId, IReadOnlyList<T> Items, string? NextCursor);
public sealed record StatusDto(int ApiVersion, string AccountId, string ComputerName, string SyncState);
public sealed record FriendDto(string Id, string DisplayName);
public sealed record VisitDto(string EventKey, string? WorldId, string? WorldName,
    string? Location, string? EnteredAt, string? ExitedAt, long? DurationMs,
    string ObservedAt, int VisitCount);
public sealed record EncounterDto(string VisitKey, string? WorldId, string? WorldName,
    string? Location, string ObservedAt);
public sealed record EncounterPage(string AccountId, int QualifiedCount, int UnknownCount,
    IReadOnlyList<EncounterDto> Items, string? NextCursor);
public sealed record BioChangeDto(long Id, string? PreviousBio, string? Bio, string ObservedAt);
public sealed record GameLocationDto(long Id, string CreatedAt, string Location,
    string? WorldId, string? WorldName, long? DurationMs);
