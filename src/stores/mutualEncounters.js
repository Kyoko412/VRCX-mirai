import { defineStore } from 'pinia';
import { ref } from 'vue';

import { userRequest } from '../api';
import { database, dbVars } from '../services/database';
import { createEncounterTracker } from '../services/mutualEncounters/encounterTracker';
import { useUserStore } from './user';

export const useMutualEncountersStore = defineStore('MutualEncounters', () => {
    const currentStatuses = ref(new Map());
    const summaries = ref(new Map());
    const activeVisitKey = ref('');
    const activeAccountId = ref('');
    const summaryRequestIds = new Map();
    let summaryEpoch = 0;

    async function refreshSummaries(userIds) {
        const ids = [...new Set(userIds.filter(Boolean))];
        if (!ids.length || !dbVars.userId) return;
        const accountId = dbVars.userId;
        const epoch = summaryEpoch;
        const requestIds = new Map(
            ids.map((id) => {
                const requestId = (summaryRequestIds.get(id) || 0) + 1;
                summaryRequestIds.set(id, requestId);
                return [id, requestId];
            })
        );
        try {
            const fetched = await database.getMutualEncounterSummaries(ids);
            if (summaryEpoch !== epoch || dbVars.userId !== accountId) return;
            const next = new Map(summaries.value);
            for (const id of ids) {
                if (summaryRequestIds.get(id) !== requestIds.get(id)) continue;
                next.delete(id);
                if (fetched.has(id)) next.set(id, fetched.get(id));
            }
            summaries.value = next;
        } catch (error) {
            console.error('Failed to load mutual encounter summaries', error);
        }
    }

    const tracker = createEncounterTracker({
        upsertCandidate: (candidate) => {
            if (dbVars.userId !== activeAccountId.value) return null;
            return database.upsertMutualEncounter(candidate);
        },
        decide: (...args) => {
            if (dbVars.userId !== activeAccountId.value) throw new Error('Encounter account changed');
            return database.setMutualEncounterDecision(...args);
        },
        lookupMutuals: async (userId) => {
            if (dbVars.userId !== activeAccountId.value) throw new Error('Encounter account changed');
            if (useUserStore().currentUser.hasSharedConnectionsOptOut) throw { status: 403 };
            const result = await userRequest.getMutualCounts({ userId });
            return result.json?.friends;
        },
        onChange: (userId, state) => {
            currentStatuses.value = new Map(currentStatuses.value).set(userId, state);
            void refreshSummaries([userId]);
        }
    });

    function enterVisit(visit) {
        if (!visit.accountId || dbVars.userId !== visit.accountId) {
            endVisit();
            return null;
        }
        const key = tracker.enterVisit(visit);
        if (key !== activeVisitKey.value || visit.accountId !== activeAccountId.value) {
            currentStatuses.value = new Map();
            if (activeAccountId.value && visit.accountId !== activeAccountId.value) {
                summaries.value = new Map();
                summaryRequestIds.clear();
                summaryEpoch++;
            }
        }
        activeVisitKey.value = key || '';
        activeAccountId.value = key ? visit.accountId : '';
        return key;
    }

    function playerJoined(player) {
        return tracker.playerJoined(player);
    }

    function playerLeft(userId) {
        tracker.playerLeft(userId);
    }

    function endVisit() {
        tracker.endVisit();
        activeVisitKey.value = '';
        activeAccountId.value = '';
        currentStatuses.value = new Map();
        summaries.value = new Map();
        summaryRequestIds.clear();
        summaryEpoch++;
    }

    async function restoreVisit(visit, players) {
        if (!enterVisit(visit)) return;
        await Promise.all(players.map((player) => tracker.playerJoined(player)));
    }

    return {
        currentStatuses,
        summaries,
        activeVisitKey,
        enterVisit,
        playerJoined,
        playerLeft,
        endVisit,
        restoreVisit,
        refreshSummaries
    };
});
