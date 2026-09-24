
# GA-FP+Div: Genetic Algorithm with Fault-Proneness Estimations and Diversification for Test Case Prioritization


## Authors and Contributors

The main contributors Yaroslav Kachanov, student of SPbPU ICSC.

The advisor and contributor Vladimir A. Parkhomenko, Senior Lecturer of SPbPU ICSC.

## Introduction

This is a research project for comparing four Test Case Prioritization (TCP) algorithms — Random, Additional (greedy), GA-Base, and the proposed GA-FP+Div (a genetic algorithm with a multi-component fitness function combining code coverage, fault-proneness via Change Impact Analysis, and structural diversity via Jaccard distance) — on the BigFaultMatrix and Defects4J datasets using APFD and APFDc metrics.

The project is completed during the preparation of Yaroslav Kachanov's work under Testing of Software at SPbPU Institute of Computer Science and Cybersecurity (SPbPU ICSC).

## License

MIT License

Input datasets used in this repository remain under the original licenses specified by their respective authors and sources:

- [BigFaultMatrix](https://github.com/dathpo/Test_Case_Prioritisation_-_Genetic_Algorithm) — see its repository for license details.
- [Defects4J](https://github.com/rjust/defects4j) — see its repository for license details.
- [tcp-methods](https://github.com/oneren38/tcp-methods) — see its repository for license details.

## Warranty

The developed software is in progress. Authors give no warranty.

## Requirements

- JDK 21 or higher
- Apache Maven 3.6 or higher

## Build

Navigate to the project directory and run:

```bash
mvn clean package
```

## Run

```bash
java -jar target/tcp-prioritization-1.0-SNAPSHOT.jar
```

## Implementations
The project contains two implementations of the genetic algorithm:

•	Branch main — implementation with OX crossover.
•	Branch feature/jenetics-ga — implementation based on the Jenetics 7.2.0 library with PMX crossover.

To switch between branches:
```
git checkout main                  # OX implementation
git checkout feature/jenetics-ga   # PMX implementation (Jenetics)
```

## References

1. Paygude P., Joshi S. D., Joshi M. Fault Aware Test Case Prioritization in Regression Testing using Genetic Algorithm // International Journal of Emerging Trends in Engineering Research. — 2020. — Vol. 8, No. 5. — P. 2112–2117. — DOI: 10.30534/ijeter/2020/104852020.
2. Mahdieh M., Mirian-Hosseinabadi S.-H., Etemadi K., Bohlouli M. Incorporating Fault-Proneness Estimations into Coverage-Based Test Case Prioritization // Information and Software Technology. — 2021. — Vol. 133. — P. 106483. — DOI: 10.1016/j.infsof.2021.106483.
3. Elbaum S., Malishevsky A. G., Rothermel G. Test Case Prioritization: A Family of Empirical Studies // IEEE Transactions on Software Engineering. — 2002. — Vol. 28, No. 2. — P. 159–182. — DOI: 10.1109/32.988497.
4. Руденко М. А., Пархоменко В. А. tcp-methods: репозиторий данных для приоритизации тестовых случаев // GitHub. — URL: https://github.com/oneren38/tcp-methods (дата обращения: 08.04.2026).
5. dathpo. Test Case Prioritisation — Genetic Algorithm: BigFaultMatrix // GitHub. — URL: https://github.com/dathpo/Test_Case_Prioritisation_-_Genetic_Algorithm (дата обращения: 08.04.2026).
6. Wilhelmstötter F. Jenetics: Java Genetic Algorithm Library. — Version 7.2.0. — 2023. — URL: https://jenetics.io (дата обращения: 25.05.2026).
