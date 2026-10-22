// Astratto Engine - the Impulsis service: rigid bodies and joints in the engine's tick, and the characters among them.
#pragma once

#include <memory>
#include <unordered_map>
#include <vector>

#include "core/engine.h"
#include "impulsis/space.h"

namespace astratto {

class ImpulsisService : public Service {
public:
    ImpulsisService() = default;

    int command(Engine& e, uint32_t type, const uint8_t* rec, uint32_t bytes) override;
    void step(Engine& e, float dt) override;
    bool owns(uint32_t kind) const override { return kind == ASTRATTO_KIND_RIGID || kind == ASTRATTO_KIND_JOINT; }
    astratto_handle reserve(uint32_t kind) override;
    bool release(Engine& e, astratto_handle h) override;
    void worldChanged(Engine& e) override;
    bool snapshot(Engine& e, uint32_t kind, std::vector<uint8_t>& out) override;
    uint32_t live(uint32_t kind) const override;

    impulsis::Space& space() { return *space_; }

private:
    struct Slot { bool joint = false; };
    void ensure(Engine& e);
    void syncMovers(Engine& e, float dt);
    void pushMovers(Engine& e, float dt);

    std::unique_ptr<impulsis::Space> space_;
    HandleTable<Slot> rigids_{ASTRATTO_KIND_RIGID};
    HandleTable<Slot> joints_{ASTRATTO_KIND_JOINT};
    std::unordered_map<astratto_handle, astratto_handle> proxyOf_;   // mover -> its capsule in the space
    std::vector<astratto_handle> seen_;
    uint64_t worldEdits_ = 0;
    const VoxelWorld* world_ = nullptr;
    uint32_t proxySerial_ = 0;
};

}  // namespace astratto
