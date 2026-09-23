using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;

namespace VRCX.MobileCompanion.Security;

public interface ICertificatePersistence
{
    X509Certificate2? Find();
    void Save(X509Certificate2 certificate);
}

public sealed record CertificateIdentity(X509Certificate2 Certificate, string SpkiSha256);

public sealed class CertificateStore(ICertificatePersistence? persistence = null)
{
    public const string HostName = "vrcx-companion.invalid";
    private readonly ICertificatePersistence _persistence = persistence ?? new CurrentUserCertificatePersistence();

    public CertificateIdentity GetOrCreate()
    {
        var existing = _persistence.Find();
        if (existing is not null && IsUsable(existing))
            return new CertificateIdentity(existing, Pin(existing));

        using var key = RSA.Create(3072);
        var request = new CertificateRequest($"CN={HostName}", key, HashAlgorithmName.SHA256,
            RSASignaturePadding.Pkcs1);
        request.CertificateExtensions.Add(new X509BasicConstraintsExtension(false, false, 0, true));
        request.CertificateExtensions.Add(new X509KeyUsageExtension(X509KeyUsageFlags.DigitalSignature, true));
        var uses = new OidCollection { new("1.3.6.1.5.5.7.3.1") };
        request.CertificateExtensions.Add(new X509EnhancedKeyUsageExtension(uses, true));
        var san = new SubjectAlternativeNameBuilder();
        san.AddDnsName(HostName);
        request.CertificateExtensions.Add(san.Build());
        using var created = request.CreateSelfSigned(DateTimeOffset.UtcNow.AddMinutes(-5),
            DateTimeOffset.UtcNow.AddYears(5));
        var persisted = X509CertificateLoader.LoadPkcs12(created.Export(X509ContentType.Pfx, string.Empty), string.Empty,
            X509KeyStorageFlags.PersistKeySet | X509KeyStorageFlags.UserKeySet | X509KeyStorageFlags.Exportable);
        persisted.FriendlyName = "VRCX Mirai Companion";
        _persistence.Save(persisted);
        return new CertificateIdentity(persisted, Pin(persisted));
    }

    private static bool IsUsable(X509Certificate2 certificate) =>
        certificate.HasPrivateKey && certificate.NotAfter > DateTime.UtcNow.AddDays(1)
        && certificate.Extensions.OfType<X509SubjectAlternativeNameExtension>()
            .Any(extension => extension.EnumerateDnsNames().Contains(HostName, StringComparer.Ordinal));

    private static string Pin(X509Certificate2 certificate)
    {
        using var key = certificate.GetRSAPublicKey()
            ?? throw new InvalidOperationException("Companion certificate has no RSA public key");
        return "sha256/" + Convert.ToBase64String(SHA256.HashData(key.ExportSubjectPublicKeyInfo()));
    }

    private sealed class CurrentUserCertificatePersistence : ICertificatePersistence
    {
        public X509Certificate2? Find()
        {
            using var store = new X509Store(StoreName.My, StoreLocation.CurrentUser);
            store.Open(OpenFlags.ReadOnly);
            return store.Certificates.OfType<X509Certificate2>()
                .Where(cert => cert.Subject == $"CN={HostName}" && cert.FriendlyName == "VRCX Mirai Companion")
                .OrderByDescending(cert => cert.NotAfter)
                .FirstOrDefault();
        }

        public void Save(X509Certificate2 certificate)
        {
            using var store = new X509Store(StoreName.My, StoreLocation.CurrentUser);
            store.Open(OpenFlags.ReadWrite);
            store.Add(certificate);
        }
    }
}
