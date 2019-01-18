package world.bentobox.bentobox.managers.island;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Util;

/**
 * @author tastybento
 */
public class IslandCache {
    @NonNull
    private Map<@NonNull Location, @NonNull Island> islandsByLocation;
    /**
     * Every player who is associated with an island is in this map.
     */
    @NonNull
    private Map<@NonNull World, @NonNull Map<@NonNull UUID, @NonNull Island>> islandsByUUID;
    @NonNull
    private Map<@NonNull World, @NonNull IslandGrid> grids;

    public IslandCache() {
        islandsByLocation = new HashMap<>();
        islandsByUUID = new HashMap<>();
        grids = new HashMap<>();
    }

    /**
     * Adds an island to the grid
     * @param island island to add, not null
     * @return true if successfully added, false if not
     */
    public boolean addIsland(@NonNull Island island) {
        if (island.getCenter() == null || island.getWorld() == null) {
            return false;
        }
        islandsByLocation.put(island.getCenter(), island);
        // Make world
        islandsByUUID.putIfAbsent(island.getWorld(), new HashMap<>());
        // Only add islands to this map if they are owned
        if (island.getOwner() != null) {
            islandsByUUID.get(island.getWorld()).put(island.getOwner(), island);
            island.getMemberSet().forEach(member -> islandsByUUID.get(island.getWorld()).put(member, island));
        }
        return addToGrid(island);
    }

    /**
     * Adds a player's UUID to the look up for islands. Does no checking
     * @param uuid - player's uuid
     * @param island - island to associate with this uuid. Only one island can be associated per world.
     */
    public void addPlayer(UUID uuid, Island island) {
        islandsByUUID.putIfAbsent(island.getWorld(), new HashMap<>());
        islandsByUUID.get(island.getWorld()).put(uuid, island);
    }

    /**
     * Adds an island to the grid register
     * @param newIsland - new island
     * @return true if successfully added, false if not
     */
    private boolean addToGrid(Island newIsland) {
        grids.putIfAbsent(newIsland.getWorld(), new IslandGrid());
        return grids.get(newIsland.getWorld()).addToGrid(newIsland);
    }

    public void clear() {
        islandsByLocation.clear();
        islandsByUUID.clear();
    }

    /**
     * Deletes an island from the database. Does not remove blocks
     * @param island - island to delete
     * @return true if successful, false if not
     */
    public boolean deleteIslandFromCache(Island island) {
        if (!islandsByLocation.remove(island.getCenter(), island) || !islandsByUUID.containsKey(island.getWorld())) {
            return false;
        }
        islandsByUUID.get(island.getWorld()).entrySet().removeIf(en -> en.getValue().equals(island));
        // Remove from grid
        grids.putIfAbsent(island.getWorld(), new IslandGrid());
        return grids.get(island.getWorld()).removeFromGrid(island);
    }

    /**
     * Get island based on the exact center location of the island
     * @param location - location to search for
     * @return island or null if it does not exist
     */
    public Island get(Location location) {
        return islandsByLocation.get(location);
    }

    /**
     * Returns island referenced by UUID
     * @param world - world to check
     * @param uuid - player
     * @return island or null if none
     */
    public Island get(World world, UUID uuid) {
        return islandsByUUID.containsKey(Util.getWorld(world)) ? islandsByUUID.get(Util.getWorld(world)).get(uuid) : null;
    }

    /**
     * Returns the island at the location or null if there is none.
     * This includes the full island space, not just the protected area
     *
     * @param location - the location
     * @return Island object
     */
    public Island getIslandAt(Location location) {
        if (location == null || !grids.containsKey(Util.getWorld(location.getWorld()))) {
            return null;
        }
        return grids.get(Util.getWorld(location.getWorld())).getIslandAt(location.getBlockX(), location.getBlockZ());
    }

    public Collection<Island> getIslands() {
        return Collections.unmodifiableCollection(islandsByLocation.values());
    }

    /**
     * @param world - world to check
     * @param uuid - uuid of player to check
     * @return set of UUID's of island members. If there is no island, this set will be empty
     */
    public Set<UUID> getMembers(World world, UUID uuid) {
        islandsByUUID.putIfAbsent(Util.getWorld(world), new HashMap<>());
        Island island = islandsByUUID.get(Util.getWorld(world)).get(uuid);
        if (island != null) {
            return island.getMemberSet();
        }
        return new HashSet<>(0);
    }

    /**
     * @param world the world to check
     * @param uuid the player's UUID
     * @return island owner's UUID, the player UUID if they are not in a team, or null if there is no island
     */
    @Nullable
    public UUID getOwner(World world, UUID uuid) {
        islandsByUUID.putIfAbsent(Util.getWorld(world), new HashMap<>());
        Island island = islandsByUUID.get(Util.getWorld(world)).get(uuid);
        if (island != null) {
            return island.getOwner();
        }
        return null;
    }

    /**
     * @param world - the world to check
     * @param uuid - the player
     * @return true if player has island and owns it
     */
    public boolean hasIsland(World world, UUID uuid) {
        islandsByUUID.putIfAbsent(Util.getWorld(world), new HashMap<>());
        Island island = islandsByUUID.get(Util.getWorld(world)).get(uuid);
        return island != null && island.getOwner().equals(uuid);
    }

    /**
     * Removes a player from the cache. If the player has an island, the island owner is removed and membership cleared.
     * The island is removed from the islandsByUUID map, but kept in the location map.
     * @param world - world
     * @param uuid - player's UUID
     * @return island player had or null if none
     */
    public Island removePlayer(World world, UUID uuid) {
        world = Util.getWorld(world);
        islandsByUUID.putIfAbsent(world, new HashMap<>());
        Island island = islandsByUUID.get(world).get(uuid);
        if (island != null) {
            if (island.getOwner() != null && island.getOwner().equals(uuid)) {
                // Clear ownership and members
                island.getMembers().clear();
                island.setOwner(null);
            } else {
                // Remove player from the island membership
                island.removeMember(uuid);
            }
        }
        islandsByUUID.get(world).remove(uuid);
        return island;
    }

    /**
     * Get the number of islands in the cache
     * @return the number of islands
     */
    public int size() {
        return islandsByLocation.size();
    }

    /**
     * Gets the number of islands in the cache for this world
     * @param world world to get the number of islands in
     * @return the number of islands
     */
    public int size(World world) {
        return islandsByUUID.getOrDefault(world, new HashMap<>(0)).size();
    }

    /**
     * Sets an island owner.
     * Clears out any other owner.
     * @param island - island
     * @param newOwnerUUID - new owner
     */
    public void setOwner(Island island, UUID newOwnerUUID) {
        island.setOwner(newOwnerUUID);
        islandsByUUID.putIfAbsent(Util.getWorld(island.getWorld()), new HashMap<>());
        islandsByUUID.get(Util.getWorld(island.getWorld())).put(newOwnerUUID, island);
        islandsByLocation.put(island.getCenter(), island);
    }
}
