// Astratto Engine - NeuralAI's route search: A* over the cells of a voxel world a body can stand in.
#pragma once

#include <cstdint>
#include <vector>

#include "world/voxel_world.h"

namespace astratto {

struct NavTuning {
    float jumpCost = 7.0f;     // a one-block ledge that has to be jumped
    float ladderCost = 1.5f;   // each rung
    float mantleCost = 3.2f;   // each block of a pull-up
};

struct PathQuery {
    float sx, sy, sz, tx, ty, tz;
    int maxExpand;
    bool doors, goalY, mantle;
    bool ladders = true;
    const std::vector<std::pair<int,float>>* penalties = nullptr;
};

/**
 * One searcher: its scratch memory is its own, so each worker thread has one and a batch of searches runs in
 * parallel over a world nobody is changing. Raw queries preserve legacy ordering and costs.
 * Agent queries opt into expiring learned penalties; their paths are
 * deliberately no longer cell-for-cell legacy equivalents. Verify reachability, permissions,
 * arrival events and measured travel instead. Search work remains bounded per update; capped
 * agents retain failure knowledge and increase their next budget instead of repeating blindly.
 */
class Pathfinder {
public:
    Pathfinder();

    /** Cells {x, y, z, ...} from the first step to the goal (or the nearest approach to it). False when boxed in. */
    bool path(const VoxelWorld& w, const NavTuning& tune, const PathQuery& q, std::vector<int32_t>& out, uint32_t& expansions);

    bool partial() const { return partial_; }
    bool capped() const { return capped_; }

    // walkability: what the search is made of, and what a caller may ask on its own
    static bool pass(const VoxelWorld& w, int x, int y, int z, bool doors);
    static bool stand(const VoxelWorld& w, int x, int y, int z, bool doors);
    static int stepY(const VoxelWorld& w, int x, int z, int y, bool doors);
    static bool climb(const VoxelWorld& w, int x, int y, int z);
    /** A body can walk the straight line from (sx, sy, sz) to (tx, tz): no walls, hazards, drops over three or steps over one that is not a stair. */
    static bool clear(const VoxelWorld& w, float sx, float sy, float sz, float tx, float tz, bool doors);
    /** The same for something with shoulders: the line and both its edges, r to either side. */
    static bool clearBody(const VoxelWorld& w, float sx, float sy, float sz, float tx, float tz, bool doors, float r);
    /** A cell a body has to go through squarely: solid on both sides of it at foot or head height (a doorway). */
    static bool choke(const VoxelWorld& w, int x, int y, int z);

private:
    static constexpr int CAP = 65536;
    static constexpr int SLOTS = 1 << 18;

    int find(int k, bool create);
    void push(int n);
    int pop();
    void relax(int from, int x, int y, int z, float cost, int gx, int gz);

    std::vector<int> nk_, parent_, heap_, slot_, slotGen_;
    std::vector<float> ng_, nf_;
    std::vector<uint8_t> closed_;
    bool partial_ = false, capped_ = false;
    const PathQuery* query_ = nullptr;
    float learnedCost(int cell) const;
    int count_ = 0, heapN_ = 0, gen_ = 0;
};

}  // namespace astratto
