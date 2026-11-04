#ifndef slic3r_SequentialPrintOrder_hpp_
#define slic3r_SequentialPrintOrder_hpp_

#include <algorithm>
#include <cstddef>
#include <vector>

namespace Slic3r {

// Ready instances can share an object index when they are copies of one model object.
// Their position in the stable fallback sequence is the deterministic tie-breaker.
struct SequentialPrintListPositionLess {
    const std::vector<size_t> &list_position;

    bool operator()(size_t lhs, size_t rhs) const
    {
        return list_position[lhs] < list_position[rhs];
    }
};

inline std::vector<size_t> sequential_print_list_positions(const std::vector<size_t> &fallback_order)
{
    std::vector<size_t> positions(fallback_order.size());
    for (size_t position = 0; position < fallback_order.size(); ++position)
        positions[fallback_order[position]] = position;
    return positions;
}

inline void insert_sequential_print_ready_instance(std::vector<size_t> &ready, size_t instance,
                                                   const SequentialPrintListPositionLess &less)
{
    const auto position = std::upper_bound(ready.begin(), ready.end(), instance, less);
    ready.insert(position, instance);
}

} // namespace Slic3r

#endif // slic3r_SequentialPrintOrder_hpp_
