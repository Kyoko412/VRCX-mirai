namespace VRCX.MobileCompanion.Data;

public interface ICompanionDb
{
    object[][] Query(string sql, IDictionary<string, object> args);
}
