<template>
    <section class="h-full min-h-0 overflow-y-auto rounded-xl bg-(--profile-card) p-4">
        <h2 class="text-base font-semibold">{{ t('dialog.user.encounters.header') }}</h2>
        <p class="mt-1 text-xs text-muted-foreground">{{ t('dialog.user.encounters.observedNote') }}</p>
        <p v-if="loading" class="py-10 text-center text-sm text-muted-foreground">
            {{ t('dialog.user.encounters.loading') }}
        </p>
        <p v-else-if="error" class="py-10 text-center text-sm text-destructive">
            {{ t('dialog.user.encounters.loadError') }}
        </p>
        <p v-else-if="!history.length" class="py-10 text-center text-sm text-muted-foreground">
            {{ t('dialog.user.encounters.empty') }}
        </p>
        <div v-else class="mt-4 space-y-2">
            <p class="text-sm text-muted-foreground">
                {{ t('dialog.user.encounters.count', { count: history.length }) }}
            </p>
            <article
                v-for="(item, index) in history"
                :key="`${item.location}:${item.observedAt}:${index}`"
                class="rounded-lg border border-border p-3 text-sm">
                <div class="font-medium">{{ item.worldName || item.location }}</div>
                <div class="mt-1 break-all text-xs text-muted-foreground">{{ item.location }}</div>
                <div class="mt-2 flex flex-wrap gap-x-5 gap-y-1 text-xs">
                    <span>{{ t('dialog.user.encounters.observedAt') }}: {{ formatTime(item.observedAt) }}</span>
                    <span>{{ t('dialog.user.encounters.mutualCount', { count: item.mutualFriendCount }) }}</span>
                </div>
            </article>
        </div>
    </section>
</template>

<script setup>
    import { ref, watch } from 'vue';
    import { useI18n } from 'vue-i18n';
    import { database, dbVars } from '../../../services/database';

    const props = defineProps({ userId: { type: String, required: true } });
    const { t } = useI18n();
    const history = ref([]);
    const loading = ref(false);
    const error = ref(false);
    let requestId = 0;

    const formatTime = (value) => new Date(value).toLocaleString();

    watch(
        () => props.userId,
        async (userId) => {
            const currentRequest = ++requestId;
            history.value = [];
            error.value = false;
            if (!userId || !dbVars.userId) return;
            loading.value = true;
            try {
                const result = await database.getMutualEncounterHistory(userId);
                if (currentRequest === requestId) history.value = result;
            } catch (cause) {
                if (currentRequest === requestId) error.value = true;
                console.error('Failed to load mutual encounter history', cause);
            } finally {
                if (currentRequest === requestId) loading.value = false;
            }
        },
        { immediate: true }
    );
</script>
