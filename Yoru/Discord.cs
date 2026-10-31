using System.IO.Pipes;
using System.Text;
using System.Text.Json.Nodes;
namespace Yoru;

/// <summary>
/// Discord Rich Presence через локальный канал Discord (discord-ipc-N): «Смотрит аниме · серия N».
/// Нужен Application ID приложения из Discord Developer Portal — он указывается в настройках Yoru.
/// Если Discord не запущен, ничего не происходит.
/// </summary>
public static class DiscordPresence
{
    static NamedPipeClientStream? pipe;
    static string connectedId = "";
    static readonly SemaphoreSlim gate = new(1, 1);
    static JsonObject? lastActivity;

    public static async Task Show(string clientId, string details, string state, string poster, long startedUnix, string link)
    {
        var activity = new JsonObject
        {
            ["type"] = 3, // «Смотрит»
            ["details"] = Clip(details, 128), ["state"] = Clip(state, 128),
            ["timestamps"] = new JsonObject { ["start"] = startedUnix },
            ["assets"] = new JsonObject { ["large_image"] = poster.StartsWith("https://") ? poster : "yoru", ["large_text"] = Clip(details, 128), ["small_image"] = "yoru", ["small_text"] = "Yoru" },
        };
        if (link.StartsWith("https://")) activity["buttons"] = new JsonArray(new JsonObject { ["label"] = "Открыть на YummyAnime", ["url"] = link });
        lastActivity = activity;
        await Send(clientId, activity);
    }
    public static Task Clear(string clientId) { lastActivity = null; return Send(clientId, null); }

    static string Clip(string text, int max) => text.Length <= max ? (text.Length < 2 ? text + "  " : text) : text[..(max - 1)] + "…";

    static async Task Send(string clientId, JsonObject? activity)
    {
        if (!System.Text.RegularExpressions.Regex.IsMatch(clientId, "^[0-9]{15,22}$")) return;
        if (!await gate.WaitAsync(0)) return;
        try
        {
            if (pipe is not { IsConnected: true } || connectedId != clientId) await Connect(clientId);
            if (pipe is not { IsConnected: true }) return;
            var payload = new JsonObject
            {
                ["cmd"] = "SET_ACTIVITY", ["nonce"] = Guid.NewGuid().ToString(),
                ["args"] = new JsonObject { ["pid"] = Environment.ProcessId, ["activity"] = activity?.DeepClone() },
            };
            await Write(1, payload);
            await Drain();
        }
        catch (Exception e) when (e is IOException or TimeoutException or ObjectDisposedException or InvalidOperationException or UnauthorizedAccessException) { Close(); }
        finally { gate.Release(); }
    }

    static async Task Connect(string clientId)
    {
        Close();
        for (var i = 0; i < 10; i++)
        {
            var candidate = new NamedPipeClientStream(".", "discord-ipc-" + i, PipeDirection.InOut, PipeOptions.Asynchronous);
            try { await candidate.ConnectAsync(250); }
            catch (Exception e) when (e is TimeoutException or IOException or UnauthorizedAccessException) { candidate.Dispose(); continue; }
            pipe = candidate; connectedId = clientId;
            await Write(0, new JsonObject { ["v"] = 1, ["client_id"] = clientId });
            await Drain();
            return;
        }
    }

    static async Task Write(int opcode, JsonObject body)
    {
        var data = Encoding.UTF8.GetBytes(body.ToJsonString());
        var frame = new byte[8 + data.Length];
        BitConverter.GetBytes(opcode).CopyTo(frame, 0);
        BitConverter.GetBytes(data.Length).CopyTo(frame, 4);
        data.CopyTo(frame, 8);
        using var timeout = new CancellationTokenSource(2000);
        await pipe!.WriteAsync(frame, timeout.Token);
        await pipe.FlushAsync(timeout.Token);
    }

    /// <summary>Читает ответ Discord (READY / подтверждение), чтобы канал не переполнялся.</summary>
    static async Task Drain()
    {
        var header = new byte[8];
        using var timeout = new CancellationTokenSource(1500);
        try
        {
            var read = 0;
            while (read < 8) { var n = await pipe!.ReadAsync(header.AsMemory(read), timeout.Token); if (n == 0) throw new IOException("closed"); read += n; }
            var opcode = BitConverter.ToInt32(header, 0); var length = BitConverter.ToInt32(header, 4);
            if (length is < 0 or > 65536) throw new IOException("bad frame");
            var body = new byte[length]; read = 0;
            while (read < length) { var n = await pipe!.ReadAsync(body.AsMemory(read), timeout.Token); if (n == 0) throw new IOException("closed"); read += n; }
            if (opcode == 2) throw new IOException("closed by Discord");
        }
        catch (OperationCanceledException) { }
    }

    static void Close() { try { pipe?.Dispose(); } catch (IOException) { } pipe = null; connectedId = ""; }
}
