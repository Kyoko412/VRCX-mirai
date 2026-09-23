using System.Security.Cryptography.X509Certificates;
using VRCX.MobileCompanion.Security;

namespace VRCX.MobileCompanion.Tests;

public sealed class CertificateStoreTests
{
    private sealed class MemoryCertificates : ICertificatePersistence
    {
        private byte[]? _pfx;
        public X509Certificate2? Find() => _pfx is null ? null : X509CertificateLoader.LoadPkcs12(_pfx, string.Empty,
            X509KeyStorageFlags.Exportable | X509KeyStorageFlags.EphemeralKeySet);
        public void Save(X509Certificate2 cert) => _pfx = cert.Export(X509ContentType.Pfx, string.Empty);
    }

    [Fact]
    public void CertificatePinSurvivesServiceRestartAndUsesFixedDnsName()
    {
        var persistence = new MemoryCertificates();
        var first = new CertificateStore(persistence).GetOrCreate();
        var second = new CertificateStore(persistence).GetOrCreate();

        Assert.Equal(first.SpkiSha256, second.SpkiSha256);
        Assert.StartsWith("sha256/", first.SpkiSha256);
        Assert.True(first.Certificate.HasPrivateKey);
        Assert.Contains("vrcx-companion.invalid", first.Certificate.Extensions
            .OfType<X509SubjectAlternativeNameExtension>().Single().EnumerateDnsNames());
    }
}
