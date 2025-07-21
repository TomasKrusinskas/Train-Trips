# Train‑Trips Processor

A Scala 3 application that:

- Parses XML files for trains, stations, and trips  
- Validates them against XSD schemas (`train.xsd`, `station.xsd`, `trip.xsd`)  
- Filters out invalid entries (syntax or schema violations)  
- Ensures version consistency for trains, stations, and trips  
- Aggregates passenger capacities per station  
- Generates summary reports and a distribution chart  

---

## Features

1. **XML + Schema Validation**  
   - Parses multiple XML files  
   - Enforces `train.xsd`, `station.xsd`, and `trip.xsd` schemas  
   - Reports parse or validation errors with file names and line numbers  

2. **Version Matching**  
   - Trip references are only accepted if the train and station share the same `version` attribute  
   - Invalid trips (missing or mismatched references) are logged and excluded  

3. **Capacity Aggregation**  
   - Computes total seat capacity per station across all valid trips  
   - Identifies the top 15 stations by passenger capacity  

4. **Reporting & Visualization**  
   - `parse_errors.txt` — XML parse or schema validation failures  
   - `invalid_trips.txt` — Trips dropped due to missing/mismatched references  
   - `top15_stations.txt` — List of top 15 stations by capacity  
   - `capacity_distribution.png` — Bar chart of station capacities sorted by station name  

---

## Prerequisites

- **Java 8+**  
- **Scala 3.3.6**  
- **Scala CLI** (for building and running)  

---

## Directory Layout

project-root/

├── XmlTrainsProcessor.scala  
├── schemas/  
│ ├── train.xsd  
│ ├── station.xsd  
│ └── trip.xsd  
└── README.md  

---

## Usage

1. **Clone** the repository:
   ```bash
   git clone https://github.com/TomasKrusinskas/Train-Trips.git
   cd Train-Trips
Prepare input and schema directories:

Create a directory input/ and place all XML files:

trains1.xml, trains2.xml, etc.

stations1.xml, stations2.xml, etc.

trips1.xml, trips2.xml, etc.

Ensure schemas/ contains:

train.xsd

station.xsd

trip.xsd

Run the processor:

scala-cli run XmlTrainsProcessor.scala -- input schemas output
input : directory with XML files

schemas : directory with XSD schemas

output : directory for generated reports and chart

Results will appear in the output/ directory:

parse_errors.txt

invalid_trips.txt

top15_stations.txt

capacity_distribution.png
