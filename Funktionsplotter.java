// starten im Command Prompt mit: java -jar lvp-0.5.0.jar --log --watch=Funktionsplotter.java
import lvp.*;
import lvp.views.*;
import lvp.skills.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.lang.Math;
import java.lang.StringBuilder;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

//Tokenizer
sealed interface Token permits Num, Op, Paren, Separator, MathematicalConstants, Variable, NumberToken, VariableToken, ConstantToken, OperatorToken, FunctionToken {
    static List<Token> tokenize(String input) {
        List<Token> tokens = new ArrayList<>();
        Pattern pattern = Pattern.compile(      // \\d = digit 0-9, + eine oder mehr Zahlen; \\. ein echter Punkt; () und ?: alles in () optional; | = oder; [+\\-*/()~] = Zeichenklassenausdruck
            "(?i)\\d+(\\.\\d+)?|" +
            "pi|e|" +
            "x|y|" +
            "sqrt|pow|log|sin|cos|tan|" +
            "[+\\-*/^()~,]"
        );    
        Matcher matcher = pattern.matcher(input);
        while (matcher.find()) {
            String token = matcher.group();
            switch (token) {
                case "+" -> tokens.add(Op.ADD);
                case "-" -> tokens.add(Op.SUB);
                case "*" -> tokens.add(Op.MUL);
                case "/" -> tokens.add(Op.DIV);
                case "~" -> tokens.add(Op.NEG);
                case "(" -> tokens.add(Paren.OPENPAREN);
                case ")" -> tokens.add(Paren.CLOSEPAREN);
                case "," -> tokens.add(Separator.COMMA);
                case "sqrt" -> tokens.add(Op.SQUAREROOT);   //z.b. sqrt(4)
                case "pow" -> tokens.add(Op.POW);           //z.B. pow(2, 3) ==> potenzieren 2^3
                case "log" -> tokens.add(Op.LOG);           //z.B. log(pi, 2)
                case "sin" -> tokens.add(Op.SIN);           //z.B. sin(pi / 2)
                case "cos" -> tokens.add(Op.COS);           //z.B. cos(pi / 2)
                case "tan" -> tokens.add(Op.TAN);           //z.B. tan(pi / 2)
                case "pi" -> tokens.add(MathematicalConstants.PI);
                case "e" -> tokens.add(MathematicalConstants.E);
                case "x" -> tokens.add(Variable.X);
                case "y" -> tokens.add(Variable.Y);
                default -> { try {
                            tokens.add(new Num(Double.parseDouble(token)));
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("Unbekanntes Token: " + token);
                    }
                }
            }
        }
        return tokens;
    }
}
//Tokenizer
//TokenizerOp
record Num(double value) implements Token {
    public String toString() { return "NUM(" + value + ")"; }
}
enum Op implements Token {
    ADD, SUB, MUL, DIV, NEG, SQUAREROOT, POW, LOG, SIN, COS, TAN;
    public static Op fromString(String s) {
        return switch (s.toLowerCase()) {
            case "+" -> ADD;
            case "-" -> SUB;
            case "*" -> MUL;
            case "/" -> DIV;
            case "~" -> NEG;
            case "sqrt" -> SQUAREROOT;
            case "pow" -> POW;
            case "log" -> LOG;
            case "sin" -> SIN;
            case "cos" -> COS;
            case "tan" -> TAN;
            default -> throw new IllegalArgumentException("Unbekannter Operator/Funktion: " + s);
        };
    }
}
enum Separator implements Token {
    COMMA;
}
enum Paren implements Token {
    OPENPAREN, CLOSEPAREN;
}
enum MathematicalConstants implements Token {
    PI,E;
    public static MathematicalConstants fromString(String s) {
        return switch (s.toLowerCase()) {
            case "pi" -> PI;
            case "e" -> E;
            default -> throw new IllegalArgumentException("Unbekannte Konstante: " + s);
        };
    }
}
enum Variable implements Token {
    X,
    Y;
}
record NumberToken(double value) implements Token {}
record VariableToken(String name) implements Token {}
record ConstantToken(String constant) implements Token {} 
record OperatorToken(String operator) implements Token {} 
record FunctionToken(String name) implements Token {}
//TokenizerOp
//InfixParser
static class InfixParser {
    private final List<Token> tokens;
    private int pos = 0;
    InfixParser(List<Token> tokens) {
        this.tokens = tokens;
    }
    private Token peek() {
        return pos < tokens.size() ? tokens.get(pos) : null;
    }
    private Token next() {
        return tokens.get(pos++);
    }
    public Node parse() {
        Node node = expr();
        if (pos < tokens.size()) {
            throw new RuntimeException("Unerwartetes Extra-Token: " + tokens.get(pos));
        }
        return node;
    }
    private Node expr() {
        Node node = term();
        while (peek() instanceof Op op && (op == Op.ADD || op == Op.SUB)) {
            next();
            Node right = term();
            node = new BinNode(op, node, right);
        }
        return node;
    }
    private Node term() {
        Node node = power();
        while (peek() instanceof Op op && (op == Op.MUL || op == Op.DIV)) {
            next();
            Node right = power();
            node = new BinNode(op, node, right);
        }
        return node;
    }
    private Node power() {
        Node node = factor();
        while (peek() instanceof Op op && op == Op.POW) {
            next();
            Node right = factor();
            node = new BinNode(op, node, right);
        }
        return node;
    }
    private Node factor() {
        Token token = peek();
        if (token instanceof Num num) {
            next();
            return new NumNode(num.value());
        } else if (token instanceof MathematicalConstants constant) {
            next();
            return new ConstNode(constant);
        } else if (token instanceof Op op && op == Op.NEG) {
            next();
            return new NegNode(factor());
        } else if (token instanceof Op op && isFunction(op)) {
            next();
            if (peek() != Paren.OPENPAREN) {
                throw new RuntimeException("Funktion erwartet '(': " + op);
            }
            next(); // '(' konsumieren
            Node arg1 = expr();
            Node arg2 = null;
            if (peek() instanceof Separator sep && sep == Separator.COMMA) {
                next(); // ',' konsumieren
                arg2 = expr();
            }
            if (peek() != Paren.CLOSEPAREN) {
                throw new RuntimeException("Fehlende schließende Klammer bei Funktion: " + op);
            }
            next(); // ')' konsumieren
            return new FuncNode(op, arg1, arg2);
        } else if (token == Paren.OPENPAREN) {
            next();
            Node inner = expr();
            if (peek() != Paren.CLOSEPAREN) {
                throw new RuntimeException("Fehlende schließende Klammer");
            }
            next();
            return inner;
        } else if (token instanceof Variable var) {
            next();
            return new VarNode(var);
        } else {
            throw new RuntimeException("Unerwartetes Token: " + token);
        }
    }
    private boolean isFunction(Op op) {
        return switch (op) {
            case SQUAREROOT, LOG, SIN, COS, TAN, POW -> true;
            default -> false;
        };
    }
}
//InfixParser
//UpnParser
public final class UpnParser {
    public static Node parse(List<Token> tokens) {
        Deque<Node> stack = new ArrayDeque<>();
        for (Token token : tokens) {
            switch (token) {
                // Zahlen
                case Num num -> {
                    stack.push(new NumNode(num.value()));
                }
                // Konstanten: pi, e
                case MathematicalConstants c -> {
                    stack.push(new ConstNode(c));
                }
                // Variablen: x, y
                case Variable v -> {
                    stack.push(new VarNode(v));
                }
                // Operatoren: +, -, *, /, ^, ...
                case Op op -> {
                    // Unäre Operatoren
                    if (op == Op.NEG) {
                        if (stack.isEmpty())
                            throw new IllegalArgumentException("Fehlendes Argument für unären Operator " + op);
                        Node arg = stack.pop();
                        stack.push(new NegNode(arg));
                    }
                    // Funktionen (unär oder binär)
                    else if (isFunction(op)) {
                        if (stack.isEmpty())
                            throw new IllegalArgumentException("Fehlendes Argument für Funktion " + op);
                        Node arg1 = stack.pop();
                        Node arg2 = null;
                        // POW braucht 2 Argumente
                        if (op == Op.POW || op == Op.LOG) {
                            if (stack.isEmpty())
                                throw new IllegalArgumentException("Funktion " + op + " braucht 2 Argumente");
                            arg2 = arg1;
                            arg1 = stack.pop();
                        }

                        stack.push(new FuncNode(op, arg1, arg2));
                    }
                    // Binäre Operatoren
                    else {
                        if (stack.size() < 2)
                            throw new IllegalArgumentException("Zu wenige Operanden für Operator " + op);
                        Node right = stack.pop();
                        Node left = stack.pop();
                        stack.push(new BinNode(op, left, right));
                    }
                }
                // Sollte nie passieren
                default -> throw new IllegalArgumentException("Unbekannter Token: " + token);
            }
        }
        if (stack.size() != 1) {
            throw new IllegalArgumentException("Ungültiger Ausdruck – Stack hat am Ende " + stack.size() + " Elemente");
        }
        return stack.pop();
    }
    private static boolean isFunction(Op op) {
        return switch (op) {
            case SQUAREROOT, LOG, SIN, COS, TAN, POW -> true;
            default -> false;
        };
    }
}
//UpnParser
//Node
sealed interface Node permits NumNode, NegNode, BinNode, ConstNode, VarNode, FuncNode {
    static double evaluate(Node node, Map<Variable, Double> env) {
        return switch (node) {
            case NumNode n -> n.value();
            case NegNode n -> -evaluate(n.inner(), env);
            case ConstNode c -> switch (c.constant()) {
                case PI -> Math.PI;
                case E -> Math.E;
            };
            case VarNode v -> env.getOrDefault(v.variable(), 0.0);
            case FuncNode f -> {
                double a = evaluate(f.arg1(), env);
                double b = f.arg2() != null ? evaluate(f.arg2(), env) : 0;
                yield switch (f.op()) {
                    case SQUAREROOT -> Math.sqrt(a);
                    case LOG -> Math.log(a);
                    case SIN -> Math.sin(a);
                    case COS -> Math.cos(a);
                    case TAN -> Math.tan(a);
                    case POW -> Math.pow(a, b);
                    default -> throw new RuntimeException("Unbekannte Funktion: " + f.op());
                };
            }
            case BinNode b -> {
                double l = evaluate(b.left(), env);
                double r = evaluate(b.right(), env);
                yield switch (b.op()) {
                    case ADD -> l + r;
                    case SUB -> l - r;
                    case MUL -> l * r;
                    case DIV -> l / r;
                    case POW -> Math.pow(l, r);
                    default -> throw new RuntimeException("Unbekannter Operator: " + b.op());
                };
            }
        };
    }
}
record NumNode(double value) implements Node {}
record NegNode(Node inner) implements Node {}
record BinNode(Op op, Node left, Node right) implements Node {}
record ConstNode(MathematicalConstants constant) implements Node {}
record VarNode(Variable variable) implements Node {}
record FuncNode(Op op, Node arg1, Node arg2) implements Node {
    public FuncNode(Op op, Node arg1) {this(op, arg1, null);}
}
//Node
//UtilParser
public static final class UtilParser {
    //tokenize
    public static Function<String, List<Token>> tokenizeString = Token::tokenize;
    //tokenize
    //tandpinfix
    public static BiFunction<String, Double, Double> tokenizeAndParseInfix = (input, x) -> {
        Map<Variable, Double> env = Map.of(
            Variable.X, x,
            Variable.Y, 1.0
        );
        List<Token> token = tokenizeString.apply(input);
        InfixParser parser = new InfixParser(token);
        Node node = parser.parse();
        return Node.evaluate(node, env);
    };
    //tandpinfix
    //tandpinfixwithoutx
    public static Function<String, Double> tokenizeAndParseWithoutXInfix = input -> {
        return tokenizeAndParseInfix.apply(input, 0.0);
    };
    //tandpinfixwithoutx
    //tandpupn
    public static BiFunction<String, Double, Double> tokenizeAndParseUpn = (input, x) -> {
        Map<Variable, Double> env = Map.of(
            Variable.X, x,
            Variable.Y, 0.0
        );
        List<Token> token = tokenizeString.apply(input);
        Node node = UpnParser.parse(token);
        return Node.evaluate(node, env);
    };
    public static Function<String, Double> tokenizeAndParseWithoutXUpn = input -> {
        return tokenizeAndParseUpn.apply(input, 0.0);
    };
    //tandpupn
    //containsxory
    public static Predicate<String> containsX = input -> {
        return Pattern.compile("[xX]").matcher(input).find();
    };
    //containsxory
    //tandp
    public static BiFunction<String, Double, Double> tokenizeAndParse = (input, x) -> {
        try {
            return tokenizeAndParseInfix.apply(input, x);
        } catch (Exception e1) {
            try {
                return tokenizeAndParseUpn.apply(input, x);
            } catch (Exception e2) {
                throw new RuntimeException("Ausdruck kann nicht ausgewertet werden!", e2);
            }
        }
    };
    //tandp
    //tandpwithoutx
    public static Function<String, Double> tokenizeAndParseWithoutX = input -> {
        try {
            return tokenizeAndParseWithoutXInfix.apply(input);
        } catch (Exception e1) {
            try {
                return tokenizeAndParseWithoutXUpn.apply(input);
            } catch (Exception e2) {
                throw new RuntimeException("Ausdruck kann nicht ausgewertet werden!", e2);
            }
        }
    };
    //tandpwithoutx
}
//UtilParser
//DotGraph
public class DotGraph {
    private final StringBuilder sb = new StringBuilder();
    private final AtomicInteger idCounter = new AtomicInteger();
    public void generate(Node node) {
        sb.append("digraph AST {\n");
        walk(node);
        sb.append("}");
    }
    public String toDot() {
        return sb.toString();
    }
    public void show(Node node) {
        generate(node);
        new Dot(600, 500).draw(toDot());
    }
    private int walk(Node node) {
        int currentId = idCounter.getAndIncrement();
        String label = switch (node) {
            case NumNode n      -> Double.toString(n.value());
            case NegNode n      -> "-";
            case ConstNode c    -> c.constant().toString();
            case VarNode v      -> v.variable().toString();
            case BinNode b      -> b.op().toString();
            case FuncNode f     -> f.op().toString();
        };
        sb.append(String.format("n%d [label=\"%s\"];\n", currentId, label));
        switch (node) {
            case NegNode n -> {
                int child = walk(n.inner());
                sb.append(String.format("n%d -> n%d;\n", currentId, child));
            }
            case BinNode b -> {
                int left = walk(b.left());
                int right = walk(b.right());
                sb.append(String.format("n%d -> n%d;\n", currentId, left));
                sb.append(String.format("n%d -> n%d;\n", currentId, right));
            }
            case FuncNode f -> {
                int arg1 = walk(f.arg1());
                sb.append(String.format("n%d -> n%d;\n", currentId, arg1));
                if (f.arg2() != null) {
                    int arg2 = walk(f.arg2());
                    sb.append(String.format("n%d -> n%d;\n", currentId, arg2));
                }
            }
            default -> {} // NumNode, ConstNode, VarNode brauchen keine Kindknoten
        }
        return currentId;
    }
    public void buildGraph(String input) {
        List<Token> tokens = Token.tokenize(input);
        try {
            Node ast = new InfixParser(tokens).parse();
            this.show(ast);
        } catch (Exception e1) {
            try {
                Node ast = UpnParser.parse(tokens);
                this.show(ast);
            } catch (Exception e2) {
                throw new RuntimeException("Ausdruck kann nicht gezeichnet werden!", e2);
            }
        }
    }
}
//DotGraph
//BetterTurtle
class BetterTurtle {
    double x, y; 
    double angle;   // 0° = Osten, 90° = Norden, 180° = Westen, 270° = Süden
    private final Turtle turtle;
    public BetterTurtle(double size) {
        this.turtle = new Turtle(0,size,0,size, size / 2 , size / 2, 0);
        this.x = size / 2;
        this.y = size / 2;
        this.angle = 0;
    }
    public BetterTurtle penUp() {
        turtle.penUp();
        return this;
    }
    public BetterTurtle penDown() {
        turtle.penDown();
        return this;
    }
    public double round(double value) {return Math.abs(value) < 1e-10 ? 0 : value;}
    public BetterTurtle forward(double distance) {
        turtle.forward(distance);
        double rad = Math.toRadians(angle);     //errechnet Radius vom Grad der Drehrichtung
        x += round(Math.cos(rad) * distance);   //berechnet mittels Drehrichtung die x-Koordinate nach distance
        y -= round(Math.sin(rad) * distance);   //berechnet mittels Drehrichtung die y-Koordinate nach distance
        return this;
    }
    public BetterTurtle backward(double distance) {
        forward(-distance);
        return this;
    }
    public BetterTurtle right(double deltaAngle) {
        angle = ((angle - deltaAngle) % 360 + 360) % 360;
        turtle.right(deltaAngle);
        return this;
    }
    public BetterTurtle left(double deltaAngle) {
        angle = ((angle + deltaAngle) % 360 + 360) % 360;
        turtle.left(deltaAngle);
        return this;
    }
    public BetterTurtle color(int r, int g, int b, double a) {
        turtle.color(r,b,g,a);
        return this;
    }
    public BetterTurtle color(int r, int b, int g) {
        turtle.color(r,b,g);
        return this;
    }
    public BetterTurtle text(String text, String font) {
        turtle.text(text,font);
        return this;
    }
    public BetterTurtle lineWidth(double w) {
        turtle.width(w);
        return this;
    }
    public BetterTurtle push() {
        turtle.push();
        return this;
    }
    public BetterTurtle pop() {
        turtle.pop();
        return this;
    }
    public BetterTurtle write() {
        turtle.write();
        return this;
    }
    //moveline
    public BetterTurtle moveTo(double newX, double newY) {
        turtle.penUp();
        goTo(newX, newY);
        turtle.penDown();
        return this;
    }
    public BetterTurtle lineTo(double newX, double newY) {
        goTo(newX, newY);
        return this;
    }
    //moveline
    //goto
    private void goTo(double newX, double newY) {
        double dx = newX - x;
        double dy = -(newY - y);
        double targetAngle = Math.toDegrees(Math.atan2(dy, dx));
        double angleDiff = (targetAngle - angle + 360) % 360;
        if (angleDiff <= 180) {
            left(angleDiff);
        } else {
            right(360 - angleDiff);
        }
        double distance = Math.hypot(dx, dy);
        forward(distance);
    }
    //goto
    public void save(String filename) {
        try {
            turtle.save(filename);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
//BetterTurtle
//CoordinateSystem
//color
public class CoordinateSystem {
    double size, middleX, middleY;
    BetterTurtle turtle;
    byte functionCounter = 0;
    double width = 0.5;
    char functionName;
    CoordinateSystem(double size) {
        this.size = size;
        this.turtle = new BetterTurtle(size);
        this.middleX = size / 2;
        this.middleY = size / 2;
        setWidth.accept(this.width);
        this.functionName = 'g';
    }
    CoordinateSystem() {this(400);}
    Runnable turtleColor = () -> {      //Verändere Farbe der Funktion bei hinzufügen einer weiteren
        switch (functionCounter) {
            case 0 -> turtle.color(255, 0, 0);
            case 1 -> turtle.color(0,255,0);
            case 2 -> turtle.color(0,0,255);
            case 3 -> turtle.color(148,225,255);
            case 4 -> turtle.color(148,45,255);
            case 5 -> turtle.color(255,45,255);
            default -> turtle.color(0,0,0);
        }
    };
    //color
    DoubleConsumer setWidth = width -> {
        turtle.lineWidth(width);
    };
    //label
    Runnable labelXAxle = () -> {       //x-Achse beschriften
        turtle.moveTo(0, size/2);
        double num = -(size/2) + size/(size/10);
        for(double i = size/(size/10); i < size; i += (size/(size/10))) {
            turtle.moveTo(i,(size/2)-(size/(size/2)))   //geht zur passenden Zinne
                .lineTo(i,(size/2)+(size/(size/2)))     //Zeichnet die Zinne 
                .penUp()
                .forward(4)     //geht zum Ort der Beschriftung
                .right(90)
                .forward(2)
                .penDown()              
                .text(num != 0 ? Integer.toString((int)num) : "  ", "3px arial-sansserif");   //beschriftet
            num += (size / (size/10));      //passe nach jedem Beschriftungsvorgang die Zahl an
        }
    };
    Runnable labelYAxle = () -> {       // y-Achse beschriften
        turtle.moveTo(size/2, size);
        double num = -(size/2) + (size/(size/10));
        for(double i = size - (size/(size/10)); i > 0; i -= (size /(size/10))) {
            turtle.moveTo((size/2)-(size/(size/2)),i)
                .lineTo((size/2) + (size/(size/2)),i)
                .penUp()
                .forward(2)
                .right(90)
                .forward(2.5)
                .penDown()
                .text(num != 0 ? Integer.toString((int)num) : "  ","3px arial-sansserif");
            num += (size / (size/10));
        }
    };
    //label
    //Axe
    Runnable arrowHead = () -> {        //Pfeil Zeichnen
        turtle.right(30)
                .backward(5)
                .forward(5)
                .left(60)
                .backward(5)
                .forward(5)
                .right(30);
    };
    Runnable xAxle = () -> {            // Zeichne x-Achse
        turtle.moveTo(0, middleY).lineTo(size, middleY);
        arrowHead.run();
        labelXAxle.run();
    };
    Runnable yAxle = () -> {            // zeichne y-Achse
        turtle.moveTo(middleX, size).lineTo(middleX, 0);
        arrowHead.run();
        labelYAxle.run();
    };
    //Axe
    Runnable background = () -> {       // Zeichne Kästchen des KoordinatenSystems
        turtle.color(0,0,0).lineWidth(0.05);
        for(double i = (size/(size/10)); i < size; i += (size/(size/10))) {
            turtle.moveTo(i,0).lineTo(i,size);
        }
        for(double i = (size/(size/10)); i < size; i += (size/(size/10))) {
            turtle.moveTo(0,i).lineTo(size,i);
        }
        turtle.lineWidth(0.01);
        for(double i = (size/(size/10))/5; i < size; i += (size/(size/10))/5) {
            turtle.moveTo(i,0).lineTo(i,size);
        }
        for(double i = (size/(size/10))/5; i < size; i += (size/(size/10))/5) {
            turtle.moveTo(0,i).lineTo(size,i);
        }
        turtle.lineWidth(0.5);
    };
    //translate
    DoubleUnaryOperator translateX = x -> {return size / 2 + x;};   //Übersetzung von SVG-Ansicht zu kartesischem KoordinatenSystem x-Koordinate
    DoubleUnaryOperator translateY = y -> {return size / 2 - y;};   //Übersetzung von SVG-Ansicht zu kartesischem KoordinatenSystem y-Koordinate
    //translate
    //draw
    Consumer<String> drawFunction = function -> {
        double startX = -size / 2;
        turtle.moveTo(translateX.applyAsDouble(startX), translateY.applyAsDouble(UtilParser.tokenizeAndParse.apply(function, startX)));
        turtleColor.run();
        for (double x = startX; x <= size / 2; x++) {
            turtle.lineTo(translateX.applyAsDouble(x), translateY.applyAsDouble(UtilParser.tokenizeAndParse.apply(function, x)));     // Zeichne Funktion durch Berechnen einer Koordinate, es wird immer eine neue weitere Koordinate mittels Parser ausgerechnet
        }
        turtle.moveTo(translateX.applyAsDouble(size/10),translateY.applyAsDouble(UtilParser.tokenizeAndParse.apply(function, (size/10) + 5))).color(1,1,1).text(functionName + "(x)", "3px arial-sansserif");  //benenne Funktion
        functionCounter++;
        functionName++;
    };
    //draw
    //coordinatemain
    void main() {
        this.xAxle.run();
        this.yAxle.run();
        this.background.run();
    }
    //coordinatemain
}
//CoordinateSystem
//FunktionsPlotter
public class FunktionsPlotter {
    //Konstruktor
    CoordinateSystem coordinateSystem;
    DotGraph dotGraph;
    List<String> functions;
    List<Double> ergebnisse;
    public FunktionsPlotter() {
        this.coordinateSystem = new CoordinateSystem(200); //Scale
        this.dotGraph = new DotGraph();
        this.functions = new ArrayList<>();
        this.ergebnisse = new ArrayList<>();
    }
    //Konstruktor
    //drawfunction
    Consumer<String> function = f -> {   //Zeichnet eine Funktion, wenn sie eine Variable enthält und fügt sie zur Liste hinzu
        if(UtilParser.containsX.test(f)) {
            coordinateSystem.drawFunction.accept(f);       
            coordinateSystem.turtle.save("output" + functions.size() + ".svg");      //Speichere Turtle-Grafik als svg, wenn eine Funktion hinzugefügt wurde
        }
    };
    //drawfunction
    //showfunction
    Runnable showFunctions = () -> {      // Zeigt die eingegebenen Funktionen und rechnet sie aus, wenn keine Variable vorhanden ist
        Clerk.write("<h5>Eingegebene Funktionen: </h5>");
        if(functions.isEmpty()) {Clerk.write("<br><p>Noch keine Funktionen eingegeben</p>");}
        ergebnisse = functions.stream().map(f -> UtilParser.containsX.test(f) ? 0.0 : UtilParser.tokenizeAndParseWithoutX.apply(f)).collect(Collectors.toList());    //testet ob eine Funktion einen Ausdruck besitzt und wendet dann den entsprechenden Tokenizer an
        IntStream.range(0, functions.size()).forEach(i -> {     // Abbilden des Graphen und des ausgewerteten Ausdrucks
            double d = ergebnisse.get(i);
            String f = functions.get(i);
            Clerk.write("<br>" + f + (UtilParser.containsX.test(f) ? "" : " = " + d));  // Wenn ein Ausdruck kein x oder y enthält, wird ein Ergebnis des Ausdrucks angezeigt
            new DotGraph().buildGraph(f);
        });
    };
    //showfunction
    Runnable clearFunctions = () -> functions.clear();
    //htmlinterface
    Runnable setHtmlInterface = () -> {
        Clerk.write("""
            <style>
                body {
                    font-family: arial;
                    }
                button {
                    cursor: pointer;
                }
                h1 {
                    justify-content: center; 
                    align-items: center; 
                    text-align: center;
                }
                h3 {
                    justify-content: center; 
                    align-items: center; 
                    text-align: center;
                }
            </style>
            <body>
            <h1>Projekt FunktionsPlotter SoSe25</h1>
            <h3>Anwendung der Schreibweise zur fehlerfreien Implementierung einer Funktion</h3>
            <table>
                <thead>
                    <tr>
                    <th>Rechenoperationen</th>
                    <th>Benutzung</th>
                    <th>Beispiel</th>
                    <th>Anmerkung</th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                    <td>Addition</td>
                    <td>+</td>
                    <td>x + 2</td>
                    <td></td>
                    </tr>
                    <tr>
                    <td>Subtraktion</td>
                    <td>-</td>
                    <td>x - 2</td>
                    <td></td>
                    </tr>
                    <tr>
                    <td>Negieren</td>
                    <td>~</td>
                    <td>~ x - 2</td>
                    <td>Nicht für Subtraktion benutzen!</td>
                    </tr>
                    <tr>
                    <td>Multiplizieren</td>
                    <td>*</td>
                    <td>2 * x</td>
                    <td>z.B. 2x funktioniert nicht!</td>
                    </tr>
                    <tr>
                    <td>Dividieren</td>
                    <td>/</td>
                    <td>1 / x</td>
                    <td></td>
                    </tr>
                    <tr>
                    <td>Potenzieren</td>
                    <td>pow(Basis, Exponent)</td>
                    <td>pow(x,2)</td>
                    <td></td>
                    </tr>
                    <tr>
                    <td>Radizieren</td>
                    <td>sqrt(Radikant)</td>
                    <td>sqrt(9)</td>
                    <td></td>
                    </tr>
                    <tr>
                    <td>Logarithmus</td>
                    <td>log(Variable, Basis)</td>
                    <td>log(x,2)</td>
                    <td></td>
                    </tr>
                    <tr>
                    <td>Sinus</td>
                    <td>sin(Variable)</td>
                    <td>sin(x)</td>
                    <td></td>
                    </tr>
                    <tr>
                    <td>Cosinus</td>
                    <td>cos(Variable)</td>
                    <td>cos(10)</td>
                    <td></td>
                    </tr>
                    <tr>
                    <td>Tangenz</td>
                    <td>tan(Variable)</td>
                    <td>tan(293)</td>
                    <td></td>
                    </tr>
                    <tr>
                    <td>Die Zahl Pi</td>
                    <td>pi</td>
                    <td>x + pi</td>
                    <td></td>
                    </tr>
                    <tr>
                    <td>Eulersche Zahl</td>
                    <td>e</td>
                    <td>e * x + 2</td>
                    <td></td>
                    </tr>
                </tbody>
                </table>
            </body>
            <br>
            <h4>Beispielfunktionen:</h4>
            <ul>
                <li><b>Übliche Notation:</b> ~pow(x, 4) + sin(pow(x,3)) + cos(pow(x,2)) + tan(pow(x,2)) + log(e, 2) + pi * (~pow(2,3)) + sqrt(9)</li>
                <li><b>Polnische Notation:</b> x 2 3 e pi 1 + sin - + * +</li>
            </ul>
        """);
        //htmlinterface
    };
    //setfunction
    Runnable setFunction = () -> {
        String function1 = "e * pow(x,2) * 1 + pow(x,3) - sin(pi/2)"; // g(x)
        if(function1 != "") {functions.add(function1);}
        String function2 = ""; // h(x)
        if(function2 != "") {functions.add(function2);}
        String function3 = ""; // i(x)
        if(function3 != "") {functions.add(function3);}
        String function4 = ""; // j(x)
        if(function4 != "") {functions.add(function4);}
    };
    //setfunction
    //setcoordinatesystem
    Runnable setCoordinateSystem = () -> {
        coordinateSystem.main();
        setFunction.run();
        for(String f : functions) {
            this.function.accept(f);
        }
        coordinateSystem.turtle.write();
        Clerk.write(Interaction.button("Groß", Interaction.eventFunction("./FunktionsPlotter.java", "//Scale", "this.coordinateSystem = new CoordinateSystem(800);")) 
                    + Interaction.button("Mittel", Interaction.eventFunction("./FunktionsPlotter.java", "//Scale", "this.coordinateSystem = new CoordinateSystem(200);")) 
                    + Interaction.button("Klein", Interaction.eventFunction("./FunktionsPlotter.java", "//Scale", "this.coordinateSystem = new CoordinateSystem(100);"))
                    );
    };
    //setcoordinatesystem
    //main
    void main() {
        Clerk.clear();
        FunktionsPlotter funktionsPlotter = new FunktionsPlotter();
        funktionsPlotter.setHtmlInterface.run();
        funktionsPlotter.setCoordinateSystem.run();
        Clerk.write("""
                <br>
                """);
        Clerk.write(Interaction.input("./FunktionsPlotter.java", "// g(x)", "String function1 = \"$\";", "Geben Sie eine Funktion ein"));
        Clerk.write(Interaction.input("./FunktionsPlotter.java", "// h(x)", "String function2 = \"$\";", "Geben Sie eine Funktion ein"));
        Clerk.write(Interaction.input("./FunktionsPlotter.java", "// i(x)", "String function3 = \"$\";", "Geben Sie eine Funktion ein"));
        Clerk.write(Interaction.input("./FunktionsPlotter.java", "// j(x)", "String function4 = \"$\";", "Geben Sie eine Funktion ein"));
        Clerk.write("""
                <br>
                """);
        funktionsPlotter.showFunctions.run();
    }
    //main
}
//FunktionsPlotter
void main() {
    FunktionsPlotter fp = new FunktionsPlotter();
    fp.main();
} 
