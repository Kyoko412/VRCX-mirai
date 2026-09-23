<template>
    <div class="x-container flex h-full min-h-0 gap-3 p-3">
        <aside class="flex w-64 shrink-0 flex-col overflow-hidden rounded-xl border border-border bg-(--profile-card)">
            <div class="border-b border-border p-3">
                <h1 class="mb-2 text-base font-semibold">{{ t('view.friend_world_visits.header') }}</h1>
                <InputGroupSearch v-model="searchTerm" :placeholder="t('view.friend_world_visits.search_friend')" />
            </div>
            <div class="min-h-0 flex-1 overflow-y-auto p-2">
                <p v-if="filteredFriends.length === 0" class="p-3 text-sm text-muted-foreground">
                    {{ t('view.friend_world_visits.no_friends') }}
                </p>
                <button
                    v-for="friend in filteredFriends"
                    :key="friend.id"
                    type="button"
                    class="mb-1 block w-full truncate rounded-md px-3 py-2 text-left text-sm hover:bg-accent"
                    :class="friend.id === selectedFriendId ? 'bg-accent font-medium' : ''"
                    :aria-pressed="friend.id === selectedFriendId"
                    @click="selectFriend(friend.id)">
                    {{ friend.displayName }}
                </button>
            </div>
        </aside>

        <main class="min-w-0 flex-1">
            <FriendWorldVisitsPanel v-if="selectedFriendId" :user-id="selectedFriendId" />
            <div
                v-else
                class="flex h-full items-center justify-center rounded-xl bg-(--profile-card) text-muted-foreground">
                {{ t('view.friend_world_visits.select_friend') }}
            </div>
        </main>
    </div>
</template>

<script setup>
    import { computed, ref } from 'vue';
    import { useI18n } from 'vue-i18n';
    import { useRoute, useRouter } from 'vue-router';

    import { InputGroupSearch } from '../../components/ui/input-group';
    import { useFriendStore } from '../../stores/friend';
    import FriendWorldVisitsPanel from './FriendWorldVisitsPanel.vue';

    const { t } = useI18n();
    const route = useRoute();
    const router = useRouter();
    const friendStore = useFriendStore();
    const searchTerm = ref('');

    const friends = computed(() =>
        [...friendStore.friends.values()]
            .map((friend) => ({
                id: friend.id,
                displayName: friend.ref?.displayName || friend.name || friend.id
            }))
            .sort((left, right) => left.displayName.localeCompare(right.displayName))
    );
    const filteredFriends = computed(() => {
        const search = searchTerm.value.trim().toLocaleLowerCase();
        return search
            ? friends.value.filter((friend) => friend.displayName.toLocaleLowerCase().includes(search))
            : friends.value;
    });
    const selectedFriendId = computed(() => {
        const requestedId = route.query.userId;
        return friends.value.find((friend) => friend.id === requestedId)?.id || friends.value[0]?.id || '';
    });

    function selectFriend(userId) {
        router.push({ name: 'friend-world-visits', query: { userId } });
    }
</script>
