<template>
    <section class="h-full min-h-0 overflow-y-auto rounded-xl bg-(--profile-card) p-4">
        <div class="mb-3 flex flex-wrap items-center justify-between gap-3">
            <div>
                <h2 class="text-base font-semibold">{{ t('view.friend_world_visits.header') }}</h2>
                <p class="mt-1 text-xs text-muted-foreground">{{ t('view.friend_world_visits.observed_note') }}</p>
            </div>
            <div class="flex items-center gap-2">
                <label for="friend-world-visits-period" class="text-sm text-muted-foreground">
                    {{ t('view.friend_world_visits.period') }}
                </label>
                <select
                    id="friend-world-visits-period"
                    v-model="period"
                    class="rounded-md border bg-background px-2 py-1.5 text-sm"
                    @change="loadVisits(userId)">
                    <option value="30">{{ t('view.friend_world_visits.last_30_days') }}</option>
                    <option value="90">{{ t('view.friend_world_visits.last_90_days') }}</option>
                    <option value="365">{{ t('view.friend_world_visits.last_year') }}</option>
                    <option value="all">{{ t('view.friend_world_visits.all_time') }}</option>
                </select>
                <button
                    type="button"
                    class="rounded-md border px-3 py-1.5 text-sm hover:bg-accent disabled:opacity-50"
                    :disabled="loading || !userId"
                    @click="loadVisits(userId)">
                    {{ t('common.actions.refresh') }}
                </button>
            </div>
        </div>

        <p v-if="loading" class="py-12 text-center text-sm text-muted-foreground">
            {{ t('view.friend_world_visits.loading') }}
        </p>
        <p v-else-if="error" class="py-12 text-center text-sm text-destructive">
            {{ t('view.friend_world_visits.load_error') }}
        </p>
        <p v-else-if="!userId || worlds.length === 0" class="py-12 text-center text-sm text-muted-foreground">
            {{ t('view.friend_world_visits.empty') }}
        </p>

        <div v-else class="space-y-3">
            <div class="text-sm text-muted-foreground">
                {{ t('view.friend_world_visits.world_count', { count: worlds.length }) }}
            </div>
            <article
                v-for="world in worlds.slice(0, visibleWorldCount)"
                :key="world.worldId"
                class="rounded-lg border border-border p-3">
                <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
                    <button
                        type="button"
                        class="min-w-0 truncate text-left font-medium hover:text-primary hover:underline"
                        :title="world.worldName"
                        @click="showWorldDialog(world.worldId)">
                        {{ world.worldName }}
                    </button>
                    <span class="shrink-0 rounded-full bg-muted px-2.5 py-1 text-xs text-muted-foreground">
                        {{ t('view.friend_world_visits.visit_count', { count: world.visitCount }) }}
                    </span>
                </div>

                <div class="space-y-2">
                    <div
                        v-for="(visit, visitIndex) in world.visits.slice(0, visibleVisitCount(world.worldId))"
                        :key="`${visit.location}:${visit.enteredAt}:${visitIndex}`"
                        class="grid grid-cols-1 gap-x-4 gap-y-1 rounded-md bg-muted/40 px-3 py-2 text-xs sm:grid-cols-3">
                        <div>
                            <span class="text-muted-foreground">{{ t('view.friend_world_visits.entered_at') }}</span>
                            <span class="ml-2 tabular-nums">{{ formatTime(visit.enteredAt) }}</span>
                        </div>
                        <div>
                            <span class="text-muted-foreground">{{ t('view.friend_world_visits.exited_at') }}</span>
                            <span class="ml-2 tabular-nums">
                                {{
                                    visit.exitedAt
                                        ? formatTime(visit.exitedAt)
                                        : t('view.friend_world_visits.exit_unknown')
                                }}
                            </span>
                        </div>
                        <div>
                            <span class="text-muted-foreground">{{ t('view.friend_world_visits.duration') }}</span>
                            <span class="ml-2 tabular-nums">{{ formatDuration(visit.durationMs) }}</span>
                        </div>
                    </div>
                    <button
                        v-if="world.visits.length > visibleVisitCount(world.worldId)"
                        type="button"
                        class="text-xs text-primary hover:underline"
                        @click="showMoreVisits(world.worldId)">
                        {{ t('view.friend_world_visits.show_more_visits') }}
                    </button>
                </div>
            </article>
            <button
                v-if="worlds.length > visibleWorldCount"
                type="button"
                class="rounded-md border px-3 py-1.5 text-sm hover:bg-accent"
                @click="visibleWorldCount += 30">
                {{ t('view.friend_world_visits.show_more_worlds') }}
            </button>
        </div>
    </section>
</template>

<script setup>
    import { onBeforeUnmount, ref, watch } from 'vue';
    import dayjs from 'dayjs';
    import { useI18n } from 'vue-i18n';

    import { showWorldDialog } from '../../coordinators/worldCoordinator';
    import { database } from '../../services/database';
    import { buildFriendWorldVisits } from './buildFriendWorldVisits';

    const props = defineProps({ userId: { type: String, required: true } });
    const { t } = useI18n();
    const worlds = ref([]);
    const period = ref('90');
    const visibleWorldCount = ref(30);
    const visibleVisitCounts = ref({});
    const loading = ref(false);
    const error = ref(false);
    let requestId = 0;

    async function loadVisits(userId) {
        const currentRequest = ++requestId;
        worlds.value = [];
        visibleWorldCount.value = 30;
        visibleVisitCounts.value = {};
        error.value = false;
        if (!userId) {
            loading.value = false;
            return;
        }
        loading.value = true;
        try {
            const dateFrom =
                period.value === 'all' ? '' : new Date(Date.now() - Number(period.value) * 86_400_000).toISOString();
            const events = await database.getFriendWorldVisitEvents(userId, dateFrom);
            if (currentRequest === requestId) {
                worlds.value = buildFriendWorldVisits(events);
            }
        } catch (loadError) {
            if (currentRequest === requestId) {
                console.error('Failed to load friend world visits', loadError);
                error.value = true;
            }
        } finally {
            if (currentRequest === requestId) {
                loading.value = false;
            }
        }
    }

    function formatTime(value) {
        return value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : t('view.friend_world_visits.time_unknown');
    }

    function visibleVisitCount(worldId) {
        return visibleVisitCounts.value[worldId] || 10;
    }

    function showMoreVisits(worldId) {
        visibleVisitCounts.value = { ...visibleVisitCounts.value, [worldId]: visibleVisitCount(worldId) + 20 };
    }

    function formatDuration(durationMs) {
        if (durationMs === null) {
            return t('view.friend_world_visits.duration_unknown');
        }
        if (durationMs < 60_000) {
            return t('view.friend_world_visits.less_than_minute');
        }
        const totalMinutes = Math.floor(durationMs / 60_000);
        if (totalMinutes >= 60) {
            return t('view.friend_world_visits.hours_minutes', {
                hours: Math.floor(totalMinutes / 60),
                minutes: totalMinutes % 60
            });
        }
        return t('view.friend_world_visits.minutes', { count: totalMinutes });
    }

    watch(() => props.userId, loadVisits, { immediate: true });
    onBeforeUnmount(() => requestId++);
</script>
