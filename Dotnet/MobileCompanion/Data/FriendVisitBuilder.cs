using System.Globalization;
using VRCX.MobileCompanion.Contract;

namespace VRCX.MobileCompanion.Data;

public sealed record VisitEvent(long Id, string CreatedAt, string Type, string? Location,
    string? WorldName, string? PreviousLocation, long? Time);

public static class FriendVisitBuilder
{
    private sealed record TimedEvent(VisitEvent Event, DateTimeOffset Time, int EventOrder);

    public static IReadOnlyList<VisitDto> Build(IEnumerable<VisitEvent> events)
    {
        var ordered = events.Select(item =>
        {
            var valid = DateTimeOffset.TryParse(item.CreatedAt, CultureInfo.InvariantCulture,
                DateTimeStyles.AssumeUniversal, out var time);
            return valid ? new TimedEvent(item, time.ToUniversalTime(), item.Type switch
            {
                "Online" => 0,
                "GPS" => 1,
                "Offline" => 2,
                _ => 3
            }) : null;
        }).Where(item => item is not null && item.EventOrder < 3)
            .OrderBy(item => item!.Time).ThenBy(item => item!.EventOrder)
            .ThenBy(item => item!.Event.Id).ToArray();

        var visits = new List<VisitDto>();
        var latestNames = new Dictionary<string, string>(StringComparer.Ordinal);
        int? currentIndex = null;

        int? AddVisit(string? location, string? worldName, string? enteredAt,
            string observedAt, long? durationMs, string eventKey)
        {
            var worldId = WorldId(location);
            if (worldId is null) return null;
            if (!string.IsNullOrEmpty(worldName)) latestNames[worldId] = worldName;
            visits.Add(new VisitDto(eventKey, worldId, worldName, location, enteredAt,
                null, durationMs, observedAt, 0));
            return visits.Count - 1;
        }

        foreach (var timed in ordered)
        {
            var item = timed!.Event;
            var location = item.Location ?? "";
            if (item.Type == "GPS" && currentIndex is int current
                && visits[current].Location == location)
                continue;

            var previousLocation = item.Type == "GPS" ? item.PreviousLocation : location;
            var duration = item.Time is >= 0 ? item.Time : null;
            if (currentIndex is int index && item.Type != "Online"
                && previousLocation == visits[index].Location)
            {
                var entered = DateTimeOffset.Parse(visits[index].EnteredAt!, CultureInfo.InvariantCulture);
                var elapsed = (long)(timed.Time - entered).TotalMilliseconds;
                if (duration is long reported && reported <= elapsed && elapsed - reported <= 30 * 60_000)
                {
                    visits[index] = visits[index] with
                    {
                        DurationMs = reported,
                        ExitedAt = entered.AddMilliseconds(reported).ToUniversalTime()
                            .ToString("yyyy-MM-ddTHH:mm:ss.fff'Z'", CultureInfo.InvariantCulture)
                    };
                }
                else if (duration is null && elapsed >= 0)
                {
                    visits[index] = visits[index] with { DurationMs = elapsed, ExitedAt = item.CreatedAt };
                }
            }
            else if (item.Type == "GPS" && !string.IsNullOrEmpty(previousLocation)
                && (currentIndex is null || previousLocation != visits[currentIndex.Value].Location))
            {
                AddVisit(previousLocation, null, null, item.CreatedAt, duration, $"gps-prev:{item.Id}");
            }

            currentIndex = item.Type == "Offline" ? null
                : AddVisit(location, item.WorldName, item.CreatedAt, item.CreatedAt,
                    null, $"{item.Type.ToLowerInvariant()}:{item.Id}");
        }

        var counts = visits.GroupBy(item => item.WorldId!, StringComparer.Ordinal)
            .ToDictionary(group => group.Key, group => group.Count(), StringComparer.Ordinal);
        return visits.Select(item => item with
        {
            WorldName = latestNames.GetValueOrDefault(item.WorldId!) ?? item.WorldId,
            VisitCount = counts[item.WorldId!]
        }).OrderByDescending(item => item.ObservedAt, StringComparer.Ordinal)
            .ThenByDescending(item => item.EventKey, StringComparer.Ordinal).ToArray();
    }

    private static string? WorldId(string? location)
    {
        if (location is null || !location.StartsWith("wrld_", StringComparison.Ordinal)) return null;
        var id = location.Split(':', 2)[0];
        return id.Length > 5 ? id : null;
    }
}
