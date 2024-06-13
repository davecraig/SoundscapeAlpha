#pragma once

#include <vector>
#include <string>
#include <utility>

namespace soundscape {

    class BeaconDescriptor {
    public:
        BeaconDescriptor(const std::vector<std::string> &filenames)
        {
            m_Filenames = filenames;
        }

        std::vector<std::string> m_Filenames;
    };

} // soundscape
