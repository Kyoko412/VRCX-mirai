using System.Text;
using System.Text.Json;

namespace VRCX.MobileCompanion.Data;

public sealed record KeysetCursor(string At, string Key)
{
    public string Encode()
    {
        var bytes = JsonSerializer.SerializeToUtf8Bytes(this);
        return Convert.ToBase64String(bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_');
    }

    public static KeysetCursor? Decode(string? cursor)
    {
        if (cursor is null) return null;
        if (cursor.Length is < 4 or > 1024 || cursor.Any(c => !char.IsAsciiLetterOrDigit(c) && c is not '-' and not '_'))
            throw new ArgumentException("Invalid cursor", nameof(cursor));
        try
        {
            var base64 = cursor.Replace('-', '+').Replace('_', '/');
            var bytes = Convert.FromBase64String(base64.PadRight((base64.Length + 3) / 4 * 4, '='));
            var value = JsonSerializer.Deserialize<KeysetCursor>(bytes);
            if (value is null || string.IsNullOrEmpty(value.At) || string.IsNullOrEmpty(value.Key)
                || value.At.Length > 128 || value.Key.Length > 128)
                throw new ArgumentException("Invalid cursor", nameof(cursor));
            return value;
        }
        catch (Exception ex) when (ex is FormatException or JsonException or DecoderFallbackException)
        {
            throw new ArgumentException("Invalid cursor", nameof(cursor), ex);
        }
    }
}
