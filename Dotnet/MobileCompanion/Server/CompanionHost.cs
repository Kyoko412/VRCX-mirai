using System.Net;
using Microsoft.AspNetCore.Hosting.Server;
using Microsoft.AspNetCore.Hosting.Server.Features;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Server.Kestrel.Core;
using Microsoft.AspNetCore.Builder;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;
using VRCX.MobileCompanion.Data;
using VRCX.MobileCompanion.Security;
using VRCX.MobileCompanion.Session;

namespace VRCX.MobileCompanion.Server;

public sealed class CompanionHost(MobileSession session, ICompanionDb db, PairingCoordinator pairing,
    DeviceRegistry devices, CertificateIdentity identity, Func<IPAddress, bool>? addressPolicy = null)
    : IAsyncDisposable
{
    private readonly Func<IPAddress, bool> _addressPolicy = addressPolicy ?? PrivateAddressSelector.CanBind;
    private WebApplication? _app;
    public bool IsRunning => _app is not null;
    public int BoundPort { get; private set; }

    public async Task StartAsync(IPAddress selectedAddress, int port, CancellationToken cancellationToken)
    {
        if (_app is not null) throw new InvalidOperationException("Companion server is already running");
        if (!_addressPolicy(selectedAddress)) throw new ArgumentException("Address is not a private LAN interface");
        if (port is < 0 or > 65535) throw new ArgumentOutOfRangeException(nameof(port));

        var builder = WebApplication.CreateBuilder(new WebApplicationOptions { Args = [] });
        builder.Logging.ClearProviders();
        builder.WebHost.ConfigureKestrel(options =>
        {
            options.Limits.MaxRequestBodySize = 4096;
            options.Listen(selectedAddress, port, listen => listen.UseHttps(identity.Certificate));
        });
        var app = builder.Build();
        CompanionRoutes.Map(app, session, new CompanionRepository(db), new GameLocationRepository(db),
            pairing, devices);
        try
        {
            await app.StartAsync(cancellationToken);
            var addresses = app.Services.GetRequiredService<IServer>().Features
                .Get<IServerAddressesFeature>()?.Addresses;
            var address = addresses?.SingleOrDefault(item => item.StartsWith("https://", StringComparison.Ordinal));
            BoundPort = address is null ? port : new Uri(address).Port;
            _app = app;
        }
        catch
        {
            await app.DisposeAsync();
            throw;
        }
    }

    public async Task StopAsync()
    {
        var app = _app;
        _app = null;
        BoundPort = 0;
        if (app is null) return;
        await app.StopAsync();
        await app.DisposeAsync();
    }

    public ValueTask DisposeAsync() => new(StopAsync());
}
