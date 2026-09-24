using System.Text.Json;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Http;
using VRCX.MobileCompanion.Contract;
using VRCX.MobileCompanion.Data;
using VRCX.MobileCompanion.Security;
using VRCX.MobileCompanion.Session;

namespace VRCX.MobileCompanion.Server;

public static class CompanionRoutes
{
    private sealed record PairRequest(string Secret, string DeviceName);
    private sealed record RedeemRequest(string RequestId, string PollSecret);

    public static void Map(WebApplication app, MobileSession session, CompanionRepository repository,
        GameLocationRepository gameLocations, PairingCoordinator pairing, DeviceRegistry devices)
    {
        app.MapPost("/v1/pair/requests", async (HttpContext context) =>
        {
            if (session.Capture() is null) return Error(409, "session_changed", "No active account");
            try
            {
                var request = await context.Request.ReadFromJsonAsync<PairRequest>(CompanionJson.Options);
                if (request is null) return Error(400, "invalid_request", "Invalid pairing request");
                var receipt = pairing.RequestPair(request.Secret, request.DeviceName,
                    context.Connection.RemoteIpAddress?.ToString());
                return Results.Json(receipt, CompanionJson.Options, statusCode: 201);
            }
            catch (Exception ex) { return MapError(ex); }
        });

        app.MapPost("/v1/pair/redeem", async (HttpContext context) =>
        {
            if (session.Capture() is null) return Error(409, "session_changed", "No active account");
            try
            {
                var request = await context.Request.ReadFromJsonAsync<RedeemRequest>(CompanionJson.Options);
                if (request is null) return Error(400, "invalid_request", "Invalid pairing request");
                var result = pairing.Redeem(request.RequestId, request.PollSecret,
                    context.Connection.RemoteIpAddress?.ToString());
                return result.State == "pending"
                    ? Results.Json(new { state = "pending" }, CompanionJson.Options, statusCode: 202)
                    : Results.Json(new { deviceId = result.DeviceId, token = result.Token },
                        CompanionJson.Options, statusCode: 200);
            }
            catch (Exception ex) { return MapError(ex); }
        });

        app.MapGet("/v1/status", (HttpContext context) => Data(context, session, devices,
            snapshot => new StatusDto(1, snapshot.AccountId, Environment.MachineName, "ready")));

        app.MapGet("/v1/friends", (HttpContext context) => Data(context, session, devices,
            snapshot => repository.ListFriends(snapshot, context.Request.Query["search"],
                context.Request.Query["cursor"].Count == 0 ? null : context.Request.Query["cursor"].ToString(),
                Limit(context))));

        app.MapGet("/v1/friends/{friendId}/world-visits", (HttpContext context, string friendId) =>
            Data(context, session, devices, snapshot => repository.GetWorldVisits(snapshot, friendId,
                Cursor(context), Limit(context))));
        app.MapGet("/v1/friends/{friendId}/encounters", (HttpContext context, string friendId) =>
            Data(context, session, devices, snapshot => repository.GetEncounters(snapshot, friendId,
                Cursor(context), Limit(context))));
        app.MapGet("/v1/friends/{friendId}/bio-history", (HttpContext context, string friendId) =>
            Data(context, session, devices, snapshot => repository.GetBioHistory(snapshot, friendId,
                Cursor(context), Limit(context))));
        app.MapGet("/v1/me/game-log", (HttpContext context) => Data(context, session, devices,
            snapshot => gameLocations.GetGameLog(snapshot, Cursor(context), Limit(context))));
    }

    private static IResult Data(HttpContext context, MobileSession session, DeviceRegistry devices,
        Func<SessionSnapshot, object> read)
    {
        var snapshot = session.Capture();
        if (snapshot is null) return Error(409, "session_changed", "Account session changed");
        var header = context.Request.Headers.Authorization.ToString();
        var token = header.StartsWith("Bearer ", StringComparison.Ordinal) ? header[7..] : null;
        var auth = devices.Authorize(token, snapshot.AccountId);
        if (auth == DeviceAuthorization.Unauthorized)
            return Error(401, "unauthorized", "Device authorization required");
        if (auth != DeviceAuthorization.Authorized)
            return Denied(auth);
        try
        {
            var result = read(snapshot);
            var payload = JsonSerializer.SerializeToUtf8Bytes(result, result.GetType(), CompanionJson.Options);
            if (!session.IsCurrent(snapshot.Generation))
                return Error(409, "session_changed", "Account session changed");
            var currentAuth = devices.Authorize(token, snapshot.AccountId);
            if (currentAuth != DeviceAuthorization.Authorized)
                return Denied(currentAuth);
            return Results.Bytes(payload, "application/json");
        }
        catch (Exception ex)
        {
            if (!session.IsCurrent(snapshot.Generation))
                return Error(409, "session_changed", "Account session changed");
            return MapError(ex);
        }
    }

    private static IResult Denied(DeviceAuthorization authorization) => authorization switch
    {
        DeviceAuthorization.WrongAccount => Error(403, "account_changed", "Desktop account changed"),
        DeviceAuthorization.Unauthorized => Error(401, "unauthorized", "Device authorization required"),
        _ => Error(403, "forbidden", "Device access denied")
    };

    private static int Limit(HttpContext context)
    {
        var value = context.Request.Query["limit"].ToString();
        if (string.IsNullOrEmpty(value)) return 20;
        if (!int.TryParse(value, out var limit) || limit is < 1 or > 50)
            throw new ArgumentException("Invalid page limit");
        return limit;
    }

    private static string? Cursor(HttpContext context) =>
        context.Request.Query["cursor"].Count == 0 ? null : context.Request.Query["cursor"].ToString();

    private static IResult MapError(Exception ex) => ex switch
    {
        FriendNotFoundException => Error(404, "not_found", "Friend is unavailable"),
        RejectedPairingException => Error(403, "pairing_rejected", "Pairing was rejected"),
        PairingRateLimitException => Error(429, "rate_limited", "Too many pairing attempts"),
        ExpiredOfferException => Error(400, "pairing_expired", "Pairing request expired"),
        InvalidPairingException => Error(400, "invalid_request", "Invalid pairing request"),
        ArgumentException { ParamName: "cursor" } => Error(400, "invalid_cursor", "Invalid cursor"),
        ArgumentException or JsonException or BadHttpRequestException =>
            Error(400, "invalid_request", "Invalid request"),
        _ => Error(503, "unavailable", "Companion data temporarily unavailable")
    };

    private static IResult Error(int status, string code, string message) =>
        Results.Json(new ApiError(code, message), CompanionJson.Options, statusCode: status);
}
