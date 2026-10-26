#pragma once
#include <array>
#include <deque>
#include <map>
#include <unordered_map>
#include <vector>
#include "core/engine.h"

namespace astratto {
/** Element-mediated material state, independent of the world's gameplay and Impulsis body ownership. */
class ChemistryService final : public Service {
public:
    int command(Engine&, uint32_t, const uint8_t*, uint32_t) override;
    void step(Engine&, float) override;
    void worldChanged(Engine&) override;
    bool snapshot(Engine&, uint32_t, std::vector<uint8_t>&) override;
private:
    struct Material { uint32_t tags = 0, burnt = 0; float fuel = 0; };
    struct Sensor {
        uint64_t id = 0, owner = 0, shock = 0;
        float x = 0, y = 0, z = 0, radius = 0.4f, fuel = 0, wet = 0, heat = 0, last = 0, emitAt = 0, chargedUntil = 0;
        uint32_t tags = 0, state = 0, block = 0, originalBlock = 0;
        bool cell = false, queued = false;
    };
    struct Volume { uint64_t serial = 0, owner = 0, source = 0; uint32_t type = 0, hops = 0; float x = 0, y = 0, z = 0, radius = 1, power = 1, life = 0.3f; };
    std::array<Material, 256> materials_{};
    std::map<uint64_t, Sensor> sensors_;
    std::unordered_map<int64_t, std::vector<uint64_t>> buckets_;
    std::deque<uint64_t> active_;
    std::deque<Volume> pending_;
    std::vector<Volume> volumes_;
    float time_ = 0, windX_ = 0, windZ_ = 0, accumulator_ = 0, rain_ = 0;
    uint64_t serial_ = 0;
    static int64_t bucket(float, float, float);
    void index(Sensor&, bool);
    void wake(Sensor&);
    void discover(Engine&, const Volume&);
    void apply(Engine&, Sensor&, const Volume&);
    void publish(Engine&, Sensor&, uint32_t);
    void emit(Volume);
};
}
