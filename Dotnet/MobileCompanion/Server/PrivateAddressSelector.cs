using System.Diagnostics;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;

namespace VRCX.MobileCompanion.Server;

public static class PrivateAddressSelector
{
    public static bool IsPrivateIpv4(IPAddress address)
    {
        if (address.AddressFamily != AddressFamily.InterNetwork) return false;
        var octets = address.GetAddressBytes();
        return octets[0] == 10 || octets[0] == 172 && octets[1] is >= 16 and <= 31
            || octets[0] == 192 && octets[1] == 168;
    }

    public static IReadOnlyList<IPAddress> Available()
    {
        if (!OperatingSystem.IsWindows()) return [];
        return NetworkInterface.GetAllNetworkInterfaces()
            .Where(nic => nic.OperationalStatus == OperationalStatus.Up && IsPhysicalLan(nic.NetworkInterfaceType))
            .Where(nic => IsPrivateProfile(nic.GetIPProperties().GetIPv4Properties()?.Index))
            .SelectMany(nic => nic.GetIPProperties().UnicastAddresses)
            .Select(item => item.Address).Where(IsPrivateIpv4).Distinct().ToArray();
    }

    public static bool CanBind(IPAddress address) => Available().Contains(address);

    private static bool IsPhysicalLan(NetworkInterfaceType type) =>
        type is NetworkInterfaceType.Ethernet or NetworkInterfaceType.GigabitEthernet
            or NetworkInterfaceType.Wireless80211;

    private static bool IsPrivateProfile(int? interfaceIndex)
    {
        if (interfaceIndex is null) return false;
        try
        {
            var system = Environment.GetFolderPath(Environment.SpecialFolder.System);
            var powershell = Path.Combine(system, "WindowsPowerShell", "v1.0", "powershell.exe");
            var start = new ProcessStartInfo(powershell)
            {
                UseShellExecute = false,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                CreateNoWindow = true,
                WindowStyle = ProcessWindowStyle.Hidden
            };
            start.ArgumentList.Add("-NoProfile");
            start.ArgumentList.Add("-NonInteractive");
            start.ArgumentList.Add("-Command");
            start.ArgumentList.Add($"(Get-NetConnectionProfile -InterfaceIndex {interfaceIndex.Value}).NetworkCategory");
            using var process = Process.Start(start);
            if (process is null || !process.WaitForExit(5000))
            {
                if (process is { HasExited: false }) process.Kill();
                return false;
            }
            return process.ExitCode == 0 && process.StandardOutput.ReadToEnd().Trim() == "Private";
        }
        catch
        {
            return false;
        }
    }
}
