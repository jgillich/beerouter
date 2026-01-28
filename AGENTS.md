# Beerouter Project Knowledge Base

## Overview

Beerouter is a routing engine for OpenStreetMap data, originally forked from BRouter. It provides
efficient routing capabilities with support for custom profiles and advanced features like nogo
areas and turn restrictions.

## Architecture

### Core Components

- **RoutingEngine**: Main routing class that performs pathfinding using A* algorithm with heuristics
- **OsmNode/OsmNodeNamed**: Represents geographic nodes with position and metadata
- **OsmLink**: Represents connections between nodes with way information
- **OsmTrack**: Contains the final route with path elements and metadata
- **NodesCache**: Efficient caching system for OSM nodes and segments
- **WaypointMatcher**: Matches waypoints to the road network

### Key Packages

- **mapaccess**: Core data structures for OSM nodes, links, and maps
- **router**: Routing algorithms and pathfinding logic
- **codec**: Data encoding/decoding for OSM segments
- **expressions**: Profile evaluation and tag processing
- **util**: Utility functions and helpers

## Coordinate System

### Integer Coordinates

- Latitude/longitude stored as integers for efficiency
- Conversion formula: `int_value = ((degrees + offset) / ILATLNG_TO_LATLNG + 0.5).toInt()`
- Offsets: +180 for longitude, +90 for latitude
- ILATLNG_TO_LATLNG constant: 1e-6 (microdegrees)

### Spatial-K Integration

- Recent updates integrate spatial-k Position objects
- Extension functions provide seamless conversion between integer and double coordinates
- `toDoubleLongitude()` and `toDoubleLatitude()` convert int to Position coordinates
- `toIntLongitude()` and `toIntLatitude()` convert Position coordinates to int

## Key Algorithms

### Routing Algorithm

- A* algorithm with distance-based heuristics
- Supports multiple passes with different coefficients
- Island detection to avoid unreachable destinations
- Dynamic range support for complex routing scenarios

### Path Optimization

- Kinematic model for realistic pathfinding
- Turn restriction handling
- Elevation-aware routing
- Voice hint generation for navigation

## Data Structures

### OsmNode

- Primary storage for geographic positions
- Now uses Position object as primary storage with iLat/iLon as derived properties
- Contains elevation data and node descriptions
- Manages links to connected nodes

### OsmLink

- Represents connections between nodes
- Stores way description bitmaps and geometry
- Supports bidirectional routing with reverse links

### Position System

- Transitioned from integer coordinates (iLat/iLon) to Position-based storage
- Maintains backward compatibility with derived iLat/iLon properties
- Uses spatial-k Position for geospatial operations

## Important Constants and Formulas

### Conversion Constants

- `ILATLNG_TO_LATLNG = 1e-6`: Conversion factor from integer to degrees
- Latitude range: -90° to +90°
- Longitude range: -180° to +180°

### Coordinate Conversion

```kotlin
// Integer to double
fun Int.toDoubleLatitude(): Double = (this * 1e-6) - 90.0
fun Int.toDoubleLongitude(): Double = (this * 1e-6) - 180.0

// Double to integer
fun Double.toIntLatitude(): Int = ((this + 90.0) / 1e-6).roundToInt()
fun Double.toIntLongitude(): Int = ((this + 180.0) / 1e-6).roundToInt()
```

### Coordinate Conversion Best Practices

- **Always use the extension methods
  from `core/src/commonMain/kotlin/dev/skynomads/beerouter/osm/coordinates.kt` for coordinate
  conversions**
- These extension methods provide accurate and consistent conversions between integer and double
  coordinates
- The methods handle the proper conversion formulas with the correct offsets and scaling factors
- Using these methods ensures consistency across the codebase and avoids manual conversion errors

## Key Features

### Advanced Routing

- Support for nogo areas and polygons
- Turn restrictions and traffic rules
- Multiple routing profiles (car, bike, foot)
- Round-trip routing with direction constraints
- Alternative route calculation

### Performance Optimizations

- Efficient caching mechanisms
- Memory management for large datasets
- Fast geometric algorithms
- Lazy evaluation where possible

## Testing and Validation

### Test Coverage

- Unit tests for core routing functionality
- Geometric algorithm validation
- Coordinate system accuracy tests
- Profile evaluation correctness

### Known Issues

- Coordinate precision changes may affect geometric algorithms
- Some tests may require tolerance adjustments due to floating-point precision
- Backward compatibility maintained through derived properties

## Development Notes

### Best Practices

- Use Position-based coordinates for new code
- Maintain backward compatibility with iLat/iLon properties
- Follow Kotlin idioms and patterns
- Leverage spatial-k library for geospatial operations

### Migration Strategy

- Existing code continues to work with iLat/iLon
- New code should prefer Position-based access
- Gradual migration path available
- Extension functions ease the transition

## Dependencies

- spatial-k: Modern geospatial library for Position objects
- Kotlin coroutines: Asynchronous operations
- SLF4J: Logging framework
- Various utility libraries for encoding/decoding

## Build and Test Instructions

### Prerequisites

- Java 11 or higher
- Gradle (wrapper provided)

### Kotlin Multiplatform Setup

Beerouter uses Kotlin Multiplatform to target multiple platforms:

#### Core Module Targets

- JVM
- Linux x64
- macOS x64
- macOS ARM64
- Windows (MinGW x64)

#### Benchmark Module Targets

The benchmark module is configured as a Kotlin Multiplatform module with:

- JVM (primary target for benchmarks)
- Linux x64
- macOS x64
- macOS ARM64
- Windows (MinGW x64)

Note: Benchmarks currently only run on the JVM target, but the infrastructure supports building for other platforms.

### Build Commands

#### Building the Project

```bash
# Build all targets
./gradlew build

# Build JVM target only
./gradlew :core:jvmJar

# Build specific platform (e.g., Linux x64)
./gradlew :core:linuxX64Binaries

# Build all native binaries
./gradlew :core:linkReleaseExecutableLinuxX64
./gradlew :core:linkReleaseExecutableMacosX64
./gradlew :core:linkReleaseExecutableMacosArm64
./gradlew :core:linkReleaseExecutableMingwX64

# Build benchmark module
./gradlew :benchmark:build
```

#### Running Tests

```bash
# Run all tests
./gradlew test

# Run JVM tests specifically
./gradlew :core:jvmTest

# Run tests with verbose output
./gradlew test --info

# Run a specific test
./gradlew :core:jvmTest --tests "*OsmNogoPolygonTest*"
```

#### Running Benchmarks

```bash
# Run JVM benchmarks (currently the only supported target)
./gradlew :benchmark:jvmBenchmark

# Generate and build all benchmarks
./gradlew :benchmark:assembleBenchmarks

# Execute all benchmarks
./gradlew :benchmark:benchmark
```

### Project Structure

- `core/` - Main Kotlin Multiplatform library with routing logic
- `benchmark/` - Kotlin Multiplatform benchmark suite using kotlinx.benchmark
- `misc/` - Additional utilities and scripts

### Common Tasks

```bash
# Clean build artifacts
./gradlew clean

# Generate IDE metadata
./gradlew idea  # For IntelliJ IDEA

# Publish to local Maven repository
./gradlew publishToMavenLocal

# Build and publish JARs
./gradlew assemble

# Build benchmark module specifically
./gradlew :benchmark:assembleBenchmarks
```

### Note on Coordinate Conversion

- Coordinate conversion extension methods are located in
  `core/src/commonMain/kotlin/dev/skynomads/beerouter/osm/coordinates.kt`
- Always use the extension methods for coordinate conversions to ensure accuracy
