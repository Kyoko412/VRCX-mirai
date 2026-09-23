using System.Text.Json;
using VRCX.MobileCompanion.Security;

namespace VRCX.MobileCompanion.Tests;

public sealed class PairingTests
{
    private sealed class ManualClock : TimeProvider
    {
        public DateTimeOffset Now { get; set; } = new(2026, 9, 24, 2, 0, 0, TimeSpan.Zero);
        public override DateTimeOffset GetUtcNow() => Now;
        public void Advance(TimeSpan by) => Now += by;
    }

    private sealed class MemoryStateStore : IDeviceStateStore
    {
        public IReadOnlyList<DeviceRecord> State { get; private set; } = [];
        public IReadOnlyList<DeviceRecord> Load() => State;
        public void Save(IReadOnlyList<DeviceRecord> state) => State = state.ToArray();
    }

    private static (PairingCoordinator Pairing, DeviceRegistry Devices, ManualClock Clock, MemoryStateStore Store)
        Create()
    {
        var store = new MemoryStateStore();
        var clock = new ManualClock();
        var devices = new DeviceRegistry(store, clock);
        var pairing = new PairingCoordinator("sha256/" + Convert.ToBase64String(new byte[32]), devices, clock);
        return (pairing, devices, clock, store);
    }

    [Fact]
    public void OfferExpiresAndItsSecretIsSingleUse()
    {
        var (pairing, _, clock, _) = Create();
        var offer = pairing.CreateOffer("192.168.1.10", 34682);
        Assert.Equal("vrcx-companion.invalid", offer.Host);
        clock.Advance(TimeSpan.FromMinutes(3));
        Assert.Throws<ExpiredOfferException>(() => pairing.RequestPair(offer.Secret, "Phone"));

        var next = pairing.CreateOffer("192.168.1.10", 34682);
        pairing.RequestPair(next.Secret, "Phone");
        Assert.Throws<ExpiredOfferException>(() => pairing.RequestPair(next.Secret, "Other phone"));
    }

    [Fact]
    public void ApprovalIsRequiredAndTokenIsAccountBoundAndRevocable()
    {
        var (pairing, devices, _, store) = Create();
        var offer = pairing.CreateOffer("192.168.1.10", 34682);
        var request = pairing.RequestPair(offer.Secret, "<My phone>");
        Assert.Equal("pending", pairing.Redeem(request.RequestId, request.PollSecret).State);
        Assert.DoesNotContain('<', pairing.ListPending().Single().DeviceName);

        pairing.Approve(request.RequestId, "usr_a");
        var redeemed = pairing.Redeem(request.RequestId, request.PollSecret);
        Assert.Equal("approved", redeemed.State);
        Assert.Equal(DeviceAuthorization.Authorized, devices.Authorize(redeemed.Token!, "usr_a"));
        Assert.Equal(DeviceAuthorization.WrongAccount, devices.Authorize(redeemed.Token!, "usr_b"));
        Assert.DoesNotContain(redeemed.Token!, JsonSerializer.Serialize(store.State));
        Assert.Throws<InvalidPairingException>(() => pairing.Redeem(request.RequestId, request.PollSecret));

        pairing.Revoke(redeemed.DeviceId!);
        Assert.Equal(DeviceAuthorization.Revoked, devices.Authorize(redeemed.Token!, "usr_a"));
    }

    [Fact]
    public void RejectionAndWrongPollSecretNeverIssueToken()
    {
        var (pairing, _, _, _) = Create();
        var offer = pairing.CreateOffer("192.168.1.10", 34682);
        var request = pairing.RequestPair(offer.Secret, "Phone");
        Assert.Throws<InvalidPairingException>(() => pairing.Redeem(request.RequestId, "wrong"));
        pairing.Reject(request.RequestId);
        Assert.Throws<RejectedPairingException>(() => pairing.Redeem(request.RequestId, request.PollSecret));
    }

    [Fact]
    public void InvalidSecretsAreRateLimitedPerSource()
    {
        var (pairing, _, _, _) = Create();
        pairing.CreateOffer("192.168.1.10", 34682);
        for (var i = 0; i < 4; i++)
            Assert.Throws<InvalidPairingException>(() => pairing.RequestPair("wrong", "Phone", "192.168.1.20"));
        Assert.Throws<PairingRateLimitException>(() => pairing.RequestPair("wrong", "Phone", "192.168.1.20"));
    }

    [Fact]
    public void InvalidDeviceNameDoesNotConsumeTheOneTimeOffer()
    {
        var (pairing, _, _, _) = Create();
        var offer = pairing.CreateOffer("192.168.1.10", 34682);
        Assert.Throws<ArgumentException>(() => pairing.RequestPair(offer.Secret, "<>"));
        Assert.NotEmpty(pairing.RequestPair(offer.Secret, "Phone").RequestId);
    }

    [Fact]
    public void EachDeviceTokenHasAtLeast256BitsOfEntropy()
    {
        var (_, devices, _, _) = Create();
        var issued = Enumerable.Range(0, 100).Select(i => devices.Issue($"Phone {i}", "usr_a")).ToArray();
        Assert.Equal(100, issued.Select(item => item.Token).Distinct().Count());
        Assert.All(issued, item => Assert.Equal(32, Convert.FromBase64String(item.Token.Replace('-', '+').Replace('_', '/')
            .PadRight((item.Token.Length + 3) / 4 * 4, '=')).Length));
    }
}
