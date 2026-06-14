#   Funktionsplotter

Dies ist ein Funktionsplotter, welcher das [Live View Programming](https://github.com/denkspuren/LiveViewProgramming) der Version 0.5.4 verwendet.
Der Funtkionsplotter selbst ist in Java geschrieben und wird mittels [Live View Programming](https://github.com/denkspuren/LiveViewProgramming) live im Browser dargestellt.
Dies hat den Vorteil, dass der Code des Funktionsplotters während der Benutzung verändert werden kann und automatisch kompiliert wird.

Jede eingegebene Funktion wird als **svg** ausgegeben.
Außerdem wird zu jeder Funktion ein Graph abgebildet, der die Funktion in Token zerlegt.

## Wie benutzt man den Funktionsplotter?

Es muss ein Terminal im Pfad des Projekts geöffnet und folgender Befehl ausgeführt werden:

```
java -jar lvp-0.5.4.jar --log --watch=Funktionsplotter.java
```

Anschließend findet man die Oberfläche im Browser unter der Adresse 

```
localhost:50001
```

oder

```
127.0.0.1:50001
```

##  Funktionsweise

Der Funktionsplotter kann mathematische Funktionen in üblicher und polnischer Notation auswerten.
Für beide besitzt dieser einen Parser und Tokenizer.

### Rechenoperationen: 

|   Rechenoperationen   |   Benutzung     |   Beispiel  |   Anmerkung   |
|-----------------------|-----------------|-------------|---------------|
|   Addition            |       +         |     x + 2   |               |
|   Subtraktion         |       -         |     x - 2   |               |
|   Negieren            |       ~         |   ~ x - 2   | Nicht für Subtraktion benutzen!  |
|   Multiplizieren      |       *         |     2 * x   | z.B. 2x funktioniert nicht! |
|   Dividieren          |       /         |     1 / x   |               |
|   Potenzieren         |pow(Basis, Exponent)| pow(x,2) |               |
|   Radizieren          | sqrt(Radikant)  |   sqrt(9)   |               |
|   Logarithmus         | log(Variable, Basis)| log(x,2)|               |
|   Sinus               |   sin(Variable) | sin(x)      |               |
|   Cosinus             |   cos(Variable) | cos(10)     |               | 
|   Tangenz             |   tan(Variable) | tan(293)    |               |
|   Die Zahl Pi         |   pi            | x + pi      |               |
|   Eulersche Zahl      |   e             | e * x + 2   |               |

### Als Variable in einem arithmetischen Ausdruck ist das ***x*** zu verwenden.

### Beispielfunktionen: 

#### Übliche Notation

```
~ pow(x, 2) - 3
```

```
e * pow(x,2) * 1 + pow(x,3) - sin(pi/2)
```

```
e * pi + pow(x,2) / sin(pi/2)
```

```
~pow(x, 4) + sin(pow(x,3)) + cos(pow(x,2)) + tan(pow(x,2)) + log(e, 2) + pi * (~pow(2,3)) + sqrt(9)
```

####  In Polnischer Notation
```
x 2 pow ~ 3 -
```

```
e x 2 pow * 1 * x 3 pow + pi 2 / sin -
```

```
e pi * x 2 pow pi 2 / sin / +
```

```
x 4 pow ~ x 3 pow sin x 2 pow cos x 2 pow tan e 2 log 2 3 pow ~ pi * 9 sqrt + + + + + +
```