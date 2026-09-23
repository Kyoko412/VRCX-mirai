using System.Text.Json;
using VRCX.MobileCompanion.Contract;

namespace VRCX.MobileCompanion.Tests;

public sealed class ContractTests
{
    [Fact]
    public void FriendPageRoundTripsWithStableIdentity()
    {
        var page = new Page<FriendDto>("usr_me", [new("usr_a", "A")], null);
        var json = JsonSerializer.Serialize(page, CompanionJson.Options);

        Assert.Equal("{\"accountId\":\"usr_me\",\"items\":[{\"id\":\"usr_a\",\"displayName\":\"A\"}],\"nextCursor\":null}", json);
        Assert.Equal("usr_a", JsonSerializer.Deserialize<Page<FriendDto>>(json, CompanionJson.Options)!.Items[0].Id);
    }

    [Fact]
    public void ErrorUsesCamelCase()
    {
        Assert.Equal("{\"code\":\"invalid_cursor\",\"message\":\"Bad cursor\"}",
            JsonSerializer.Serialize(new ApiError("invalid_cursor", "Bad cursor"), CompanionJson.Options));
    }
}
