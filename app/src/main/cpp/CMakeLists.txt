cmake_minimum_required(VERSION 3.22.1)
project("rnnoise_wrapper")

# Add the native library
add_library(
    rnnoise_wrapper
    SHARED
    native-lib.cpp
)

# Find log library
find_library(
    log-lib
    log
)

# Link the native library with log
target_link_libraries(
    rnnoise_wrapper
    ${log-lib}
)
