import { dbVars } from './index.js';
import sqliteService from '../sqlite.js';

function tableName() {
    return `${dbVars.userPrefix}_mutual_encounters_v1`;
}

const mutualEncounters = {
    async upsertMutualEncounter(candidate) {
        if (!dbVars.userPrefix) return null;
        const table = tableName();
        await sqliteService.executeNonQuery(
            `INSERT OR IGNORE INTO ${table} (visit_key, other_user_id, location, observed_at, display_name_snapshot, world_name_snapshot, status)
             VALUES (@visitKey, @userId, @location, @observedAt, @displayName, @worldName, 'unknown')`,
            {
                '@visitKey': candidate.visitKey,
                '@userId': candidate.userId,
                '@location': candidate.location,
                '@observedAt': candidate.observedAt,
                '@displayName': candidate.displayName || '',
                '@worldName': candidate.worldName || ''
            }
        );
        let result = null;
        await sqliteService.execute(
            (row) => {
                result = { status: row[0], mutualFriendCount: row[1], checkedAt: row[2] };
            },
            `SELECT status, mutual_friend_count, checked_at FROM ${table}
             WHERE visit_key = @visitKey AND other_user_id = @userId`,
            { '@visitKey': candidate.visitKey, '@userId': candidate.userId }
        );
        return result;
    },

    async setMutualEncounterDecision(visitKey, userId, status, mutualFriendCount, checkedAt) {
        if (!dbVars.userPrefix || !['qualified', 'not_qualified'].includes(status)) return;
        await sqliteService.executeNonQuery(
            `UPDATE ${tableName()} SET status = @status, mutual_friend_count = @count, checked_at = @checkedAt
             WHERE visit_key = @visitKey AND other_user_id = @userId AND status = 'unknown'`,
            {
                '@visitKey': visitKey,
                '@userId': userId,
                '@status': status,
                '@count': mutualFriendCount,
                '@checkedAt': checkedAt
            }
        );
    },

    async getMutualEncounterSummaries(userIds) {
        const summaries = new Map();
        if (!dbVars.userPrefix || !userIds.length) return summaries;
        const params = {};
        const placeholders = userIds.map((userId, index) => {
            params[`@user${index}`] = userId;
            return `@user${index}`;
        });
        await sqliteService.execute(
            (row) => {
                summaries.set(row[0], { qualifiedCount: row[1], unknownCount: row[2] });
            },
            `SELECT other_user_id,
                    SUM(CASE WHEN status = 'qualified' THEN 1 ELSE 0 END),
                    SUM(CASE WHEN status = 'unknown' THEN 1 ELSE 0 END)
             FROM ${tableName()} WHERE other_user_id IN (${placeholders.join(', ')}) GROUP BY other_user_id`,
            params
        );
        return summaries;
    },

    async getMutualEncounterHistory(userId) {
        const history = [];
        if (!dbVars.userPrefix || !userId) return history;
        await sqliteService.execute(
            (row) => {
                history.push({ observedAt: row[0], location: row[1], worldName: row[2], mutualFriendCount: row[3] });
            },
            `SELECT observed_at, location, world_name_snapshot, mutual_friend_count
             FROM ${tableName()} WHERE other_user_id = @userId AND status = 'qualified'
             ORDER BY observed_at DESC`,
            { '@userId': userId }
        );
        return history;
    }
};

export { mutualEncounters };
