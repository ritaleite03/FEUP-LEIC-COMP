# Comp2024 Project

## Group members

- José Ribeiro (up202108868@edu.fe.up.pt)
- Rita Leite (up202105309@edu.fe.up.pt)
- Tiago Azevedo (up202108699@edu.fe.up.pt)

## Work Distribution

- José Ribeiro - 39.67 %
- Rita Leite - 33.33 %
- Tiago Azevedo - 27 %

## Self-Assessment

- We think our project deserves a 19.5 out of 20.

## Extra Elements

Our project also supports some extra AST optimizations:


For a variable `a`:
- `a` + 0 -> `a`
- 0 + `a` -> `a`
- `a` - 0 -> `a`
- `a` - `a` -> 0
- `a` * 1 -> `a`
- 1 * `a` -> `a`
- `a` / 1 -> `a`
- `a` / `a` -> 1
- `a` < `a` -> false
- `a` && `a` -> `a`
- true && `a` -> `a`
- `a` && true -> `a`
