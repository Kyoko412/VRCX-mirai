<template>
    <Dialog :open="open" @update:open="emit('update:open', $event)">
        <DialogContent class="x-dialog sm:max-w-225 max-h-[85vh] flex flex-col">
            <DialogHeader>
                <DialogTitle>{{ t('dialog.user.info.bio_history_title') }} · {{ displayName }}</DialogTitle>
                <DialogDescription>{{ t('dialog.user.info.bio_history_observed_note') }}</DialogDescription>
            </DialogHeader>

            <div class="min-h-0 overflow-y-auto space-y-3 pr-1">
                <section class="rounded-xl bg-(--profile-card) p-3">
                    <div class="flex items-center justify-between gap-2 text-xs text-muted-foreground mb-2">
                        <span>{{ t('dialog.user.info.bio_history_current') }}</span>
                        <span v-if="hasCurrentBio && rows.length && rows[0].bio === currentBio">
                            {{ t('dialog.user.info.bio_history_last_observed') }}
                            {{ formatDateFilter(rows[0].observedAt, 'long') }}
                        </span>
                        <span v-else-if="hasCurrentBio">{{ t('dialog.user.info.bio_history_current_unknown') }}</span>
                    </div>
                    <pre class="text-sm font-[inherit] whitespace-pre-wrap break-words">{{
                        hasCurrentBio
                            ? currentBio || t('dialog.user.info.bio_history_empty_bio')
                            : t('dialog.user.info.bio_history_current_unavailable')
                    }}</pre>
                </section>

                <div class="text-sm text-muted-foreground">
                    {{ t('dialog.user.info.bio_history_count', { count }) }}
                </div>

                <div v-if="error" class="rounded-xl border border-destructive/40 p-3 text-sm">
                    <p>{{ t('dialog.user.info.bio_history_error') }}</p>
                    <Button data-testid="bio-history-retry" variant="outline" class="mt-2" @click="retry">
                        {{ t('dialog.user.info.bio_history_retry') }}
                    </Button>
                </div>
                <div v-else-if="loading && !rows.length" class="text-sm text-muted-foreground">
                    {{ t('dialog.user.info.bio_history_loading') }}
                </div>
                <div v-else-if="!rows.length" class="text-sm text-muted-foreground">
                    {{ t('dialog.user.info.bio_history_none') }}
                </div>

                <section v-for="(row, index) in rows" :key="row.id" class="rounded-xl border border-border p-3">
                    <div class="flex items-center justify-between gap-2 mb-2 text-xs text-muted-foreground">
                        <span>{{ t('dialog.user.info.bio_history_change', { count: count - index }) }}</span>
                        <time :datetime="row.observedAt">{{ formatDateFilter(row.observedAt, 'long') }}</time>
                    </div>
                    <div
                        class="bio-diff text-sm whitespace-pre-wrap break-words"
                        :class="{ 'max-h-32 overflow-hidden': !expanded.has(row.id) }"
                        v-html="formatBioDifference(row.previousBio, row.bio)"></div>
                    <Button variant="ghost" size="sm" class="mt-2" @click="toggleExpanded(row.id)">
                        {{
                            expanded.has(row.id)
                                ? t('dialog.user.info.bio_history_hide_full')
                                : t('dialog.user.info.bio_history_show_full')
                        }}
                    </Button>
                    <div v-if="expanded.has(row.id)" class="grid gap-2 mt-2 text-xs">
                        <div>
                            <div class="text-muted-foreground mb-1">{{ t('dialog.user.info.bio_history_before') }}</div>
                            <pre class="font-[inherit] whitespace-pre-wrap break-words">{{
                                row.previousBio || t('dialog.user.info.bio_history_empty_bio')
                            }}</pre>
                        </div>
                        <div>
                            <div class="text-muted-foreground mb-1">{{ t('dialog.user.info.bio_history_after') }}</div>
                            <pre class="font-[inherit] whitespace-pre-wrap break-words">{{
                                row.bio || t('dialog.user.info.bio_history_empty_bio')
                            }}</pre>
                        </div>
                    </div>
                </section>

                <Button
                    v-if="nextCursor && !error"
                    variant="outline"
                    class="w-full"
                    :disabled="loading"
                    @click="loadMore">
                    {{
                        loading
                            ? t('dialog.user.info.bio_history_loading')
                            : t('dialog.user.info.bio_history_load_more')
                    }}
                </Button>
            </div>
        </DialogContent>
    </Dialog>
</template>

<script setup>
    import { computed, ref, watch } from 'vue';
    import { useI18n } from 'vue-i18n';
    import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
    import { Button } from '@/components/ui/button';

    import { database } from '../../../services/database';
    import { formatDateFilter } from '../../../shared/utils';
    import { formatBioDifference } from '../../../shared/utils/bioDiff';

    const props = defineProps({
        open: { type: Boolean, required: true },
        userId: { type: String, default: '' },
        displayName: { type: String, default: '' },
        currentBio: { type: String, default: null }
    });
    const emit = defineEmits(['update:open']);
    const { t } = useI18n();
    const hasCurrentBio = computed(() => typeof props.currentBio === 'string');
    const rows = ref([]);
    const count = ref(0);
    const nextCursor = ref(null);
    const expanded = ref(new Set());
    const loading = ref(false);
    const error = ref(false);
    let requestId = 0;

    watch(
        () => [props.open, props.userId],
        ([open, userId]) => {
            requestId++;
            rows.value = [];
            count.value = 0;
            nextCursor.value = null;
            expanded.value = new Set();
            error.value = false;
            loading.value = false;
            if (open && userId) loadInitial();
        },
        { immediate: true }
    );

    async function loadInitial() {
        if (!props.userId || loading.value) return;
        const activeRequest = ++requestId;
        loading.value = true;
        error.value = false;
        try {
            const [total, page] = await Promise.all([
                database.getFriendBioHistoryCount(props.userId),
                database.getFriendBioHistory(props.userId)
            ]);
            if (activeRequest !== requestId) return;
            count.value = total;
            rows.value = page.rows;
            nextCursor.value = page.nextCursor;
        } catch {
            if (activeRequest === requestId) error.value = true;
        } finally {
            if (activeRequest === requestId) loading.value = false;
        }
    }

    async function loadMore() {
        if (!nextCursor.value || loading.value) return;
        const activeRequest = ++requestId;
        loading.value = true;
        error.value = false;
        try {
            const page = await database.getFriendBioHistory(props.userId, { cursor: nextCursor.value });
            if (activeRequest !== requestId) return;
            rows.value.push(...page.rows);
            nextCursor.value = page.nextCursor;
        } catch {
            if (activeRequest === requestId) error.value = true;
        } finally {
            if (activeRequest === requestId) loading.value = false;
        }
    }

    function retry() {
        if (rows.value.length) loadMore();
        else loadInitial();
    }

    function toggleExpanded(id) {
        const next = new Set(expanded.value);
        if (next.has(id)) next.delete(id);
        else next.add(id);
        expanded.value = next;
    }
</script>

<style scoped>
    .bio-diff :deep(.x-text-removed) {
        color: #ef4444;
        background: rgb(239 68 68 / 18%);
        text-decoration: line-through;
        border-radius: 4px;
    }

    .bio-diff :deep(.x-text-added) {
        color: #22c55e;
        background: rgb(34 197 94 / 18%);
        border-radius: 4px;
    }
</style>
