package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/*
 *  Since the resolver needs to visit every node in the syntax tree, it implements the
    visitor abstraction we already have in place. Only a few kinds of nodes are
    interesting when it comes to resolving variables:
        A block statement introduces a new scope for the statements it contains.
        A function declaration introduces a new scope for its body and binds its
            parameters in that scope.
        A variable declaration adds a new variable to the current scope.
        Variable and assignment expressions need to have their variables resolved.
 */

class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
    private final Interpreter interpreter;

    private Map<String, Boolean> globalScope = new HashMap<>();
    private Stack<Boolean> inLoop = new Stack<>();
    private Stack<Boolean> breakUsedInLoop = new Stack<>();

    private final Stack<Map<String, Boolean>> scopes = new Stack<>();
    private FunctionType currentFunction = FunctionType.NONE;

    Resolver(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    private enum FunctionType {
        NONE,
        FUNCTION
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        beginScope();
        resolve(stmt.statements);
        endScope();
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        declare(stmt.name);
        define(stmt.name);
        resolveFunction(stmt, FunctionType.FUNCTION);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        resolve(stmt.condition);
        resolve(stmt.thenBranch);
        if (stmt.elseBranch != null)
            resolve(stmt.elseBranch);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        if (currentFunction == FunctionType.NONE) {
            Lox.error(stmt.keyword, "Can't return from top-level code.");
        }
        if (stmt.value != null) {
            resolve(stmt.value);
        }
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        startLoop();
        resolve(stmt.condition);
        // If 'break' was used within the loop, no need to resolve the body
        if (!breakUsedInLoop.peek()) {
            resolveLoopBody(stmt.body);
        }
        endLoop();
        return null;
    }

    @Override
    public Void visitBreakStmt(Stmt.Break stmt) {
        checkBreak(stmt.keyword);
        return null;
    }

    /*
     * We split binding into two steps, declaring then defining in order to handle
     * funny edge cases like this:
     * var a = "outer";
     * {
     * var a = a;
     * }
     */
    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        declare(stmt.name);
        if (stmt.initializer != null) {
            resolve(stmt.initializer);
        }
        define(stmt.name);
        return null;
    }

    @Override
    public Void visitAssignExpr(Expr.Assign expr) {
        resolve(expr.value);
        resolveLocal(expr, expr.name);
        return null;
    }

    @Override
    public Void visitBinaryExpr(Expr.Binary expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitCallExpr(Expr.Call expr) {
        resolve(expr.callee);
        for (Expr argument : expr.arguments) {
            resolve(argument);
        }
        return null;
    }

    @Override
    public Void visitGroupingExpr(Expr.Grouping expr) {
        resolve(expr.expression);
        return null;
    }

    @Override
    public Void visitLiteralExpr(Expr.Literal expr) {
        return null;
    }

    @Override
    public Void visitLogicalExpr(Expr.Logical expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitUnaryExpr(Expr.Unary expr) {
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitVariableExpr(Expr.Variable expr) {
        if (!scopes.isEmpty() &&
                scopes.peek().get(expr.name.lexeme) == Boolean.FALSE) {
            Lox.error(expr.name,
                    "Can't read local variable in its own initializer.");
        }
        resolveLocal(expr, expr.name);
        return null;
    }

    void resolve(List<Stmt> statements) {
        for (Stmt statement : statements) {
            resolve(statement);
        }
    }

    private void resolve(Stmt stmt) {
        stmt.accept(this);
    }

    private void resolve(Expr expr) {
        expr.accept(this);
    }

    private void resolveFunction(
            Stmt.Function function, FunctionType type) {
        FunctionType enclosingFunction = currentFunction;
        currentFunction = type;
        beginScope();
        for (Token param : function.params) {
            declare(param);
            define(param);
        }
        resolve(function.body);
        endScope();
        currentFunction = enclosingFunction;
    }

    private void resolveLoopBody(Stmt stmt) {
        startLoop();
        beginScope();
        stmt.accept(this);
        endLoop();
        endScope();
    }

    private void beginScope() {
        scopes.push(new HashMap<String, Boolean>());
    }

    private void endScope() {
        scopes.pop();
    }

    // Call this method when entering a loop
    private void startLoop() {
        inLoop.push(true);
        breakUsedInLoop.push(false);
    }

    // Call this method when exiting a loop
    private void endLoop() {
        inLoop.pop();
        breakUsedInLoop.pop();
    }

    // Call this method when encountering a break statement
    private void checkBreak(Token keyword) {
        if (inLoop.isEmpty() || !inLoop.peek()) {
            Lox.error(keyword, "Cannot use 'break' outside of a loop.");
        } else {
            breakUsedInLoop.pop();
            breakUsedInLoop.push(true);
        }
    }

    private void declare(Token name) {
        if (scopes.isEmpty()) {
            // No scopes, meaning we're in the global scope
            if (globalScope.containsKey(name.lexeme)) {
                Lox.error(name, "Variable with this name is already declared in the global scope.");
            }
            globalScope.put(name.lexeme, false);
        } else {
            Map<String, Boolean> currentScope = scopes.peek();

            // Check for local or global variable declaration
            if (currentScope.containsKey(name.lexeme) || globalScope.containsKey(name.lexeme)) {
                Lox.error(name, "Variable with this name is already declared in this scope.");
            }

            currentScope.put(name.lexeme, false);
        }
    }

    private void define(Token name) {
        if (scopes.isEmpty())
            return;
        scopes.peek().put(name.lexeme, true);
    }

    private void resolveLocal(Expr expr, Token name) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (scopes.get(i).containsKey(name.lexeme)) {
                interpreter.resolve(expr, scopes.size() - 1 - i);
                return;
            }
        }
    }
}
