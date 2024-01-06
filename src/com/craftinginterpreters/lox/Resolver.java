//> Resolving and Binding resolver
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

  // > scopes-field
  private final Stack<Map<String, Boolean>> scopes = new Stack<>();
  // < scopes-field
  // > function-type-field
  private FunctionType currentFunction = FunctionType.NONE;
  // < function-type-field

  Resolver(Interpreter interpreter) {
    this.interpreter = interpreter;
  }

  // > function-type
  private enum FunctionType {
    NONE,
    /*
     * Resolving and Binding function-type < Classes function-type-method
     * FUNCTION
     */
    // > Classes function-type-method
    FUNCTION,
    // > function-type-initializer
    INITIALIZER,
    // < function-type-initializer
    METHOD
    // < Classes function-type-method
  }
  // < function-type
  // > Classes class-type

  private enum ClassType {
    NONE,
    /*
     * Classes class-type < Inheritance class-type-subclass
     * CLASS
     */
    // > Inheritance class-type-subclass
    CLASS,
    SUBCLASS
    // < Inheritance class-type-subclass
  }

  private ClassType currentClass = ClassType.NONE;

  // < Classes class-type
  // > resolve-statements
  void resolve(List<Stmt> statements) {
    for (Stmt statement : statements) {
      resolve(statement);
    }
  }

  // < resolve-statements
  // > visit-block-stmt
  @Override
  public Void visitBlockStmt(Stmt.Block stmt) {
    beginScope();
    resolve(stmt.statements);
    endScope();
    return null;
  }

  // < visit-block-stmt
  // > Classes resolver-visit-class
  @Override
  public Void visitClassStmt(Stmt.Class stmt) {
    // > set-current-class
    ClassType enclosingClass = currentClass;
    currentClass = ClassType.CLASS;

    // < set-current-class
    declare(stmt.name);
    define(stmt.name);
    // > Inheritance resolve-superclass

    // > inherit-self
    if (stmt.superclass != null &&
        stmt.name.lexeme.equals(stmt.superclass.name.lexeme)) {
      Lox.error(stmt.superclass.name,
          "A class can't inherit from itself.");
    }

    // < inherit-self
    if (stmt.superclass != null) {
      // > set-current-subclass
      currentClass = ClassType.SUBCLASS;
      // < set-current-subclass
      resolve(stmt.superclass);
    }
    // < Inheritance resolve-superclass
    // > Inheritance begin-super-scope

    if (stmt.superclass != null) {
      beginScope();
      scopes.peek().put("super", true);
    }
    // < Inheritance begin-super-scope
    // > resolve-methods

    // > resolver-begin-this-scope
    beginScope();
    scopes.peek().put("this", true);

    // < resolver-begin-this-scope
    for (Stmt.Function method : stmt.methods) {
      FunctionType declaration = FunctionType.METHOD;
      // > resolver-initializer-type
      if (method.name.lexeme.equals("init")) {
        declaration = FunctionType.INITIALIZER;
      }

      // < resolver-initializer-type
      resolveFunction(method, declaration); // [local]
    }

    // > resolver-end-this-scope
    endScope();

    // < resolver-end-this-scope
    // < resolve-methods
    // > Inheritance end-super-scope
    if (stmt.superclass != null)
      endScope();

    // < Inheritance end-super-scope
    // > restore-current-class
    currentClass = enclosingClass;
    // < restore-current-class
    return null;
  }

  // < Classes resolver-visit-class
  // > visit-expression-stmt
  @Override
  public Void visitExpressionStmt(Stmt.Expression stmt) {
    resolve(stmt.expression);
    return null;
  }

  // < visit-expression-stmt
  // > visit-function-stmt
  @Override
  public Void visitFunctionStmt(Stmt.Function stmt) {
    declare(stmt.name);
    define(stmt.name);

    /*
     * Resolving and Binding visit-function-stmt < Resolving and Binding
     * pass-function-type
     * resolveFunction(stmt);
     */
    // > pass-function-type
    resolveFunction(stmt, FunctionType.FUNCTION);
    // < pass-function-type
    return null;
  }

  // < visit-function-stmt
  // > visit-if-stmt
  @Override
  public Void visitIfStmt(Stmt.If stmt) {
    resolve(stmt.condition);
    resolve(stmt.thenBranch);
    if (stmt.elseBranch != null)
      resolve(stmt.elseBranch);
    return null;
  }

  // < visit-if-stmt
  // > visit-print-stmt
  @Override
  public Void visitPrintStmt(Stmt.Print stmt) {
    resolve(stmt.expression);
    return null;
  }

  // < visit-print-stmt
  // > visit-return-stmt
  @Override
  public Void visitReturnStmt(Stmt.Return stmt) {
    // > return-from-top
    if (currentFunction == FunctionType.NONE) {
      Lox.error(stmt.keyword, "Can't return from top-level code.");
    }

    // < return-from-top
    if (stmt.value != null) {
      // > Classes return-in-initializer
      if (currentFunction == FunctionType.INITIALIZER) {
        Lox.error(stmt.keyword,
            "Can't return a value from an initializer.");
      }

      // < Classes return-in-initializer
      resolve(stmt.value);
    }

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
  // < visit-return-stmt
  // > visit-var-stmt
  @Override
  public Void visitVarStmt(Stmt.Var stmt) {
    declare(stmt.name);
    if (stmt.initializer != null) {
      resolve(stmt.initializer);
    }
    define(stmt.name);
    return null;
  }

  // < visit-var-stmt
  // > visit-while-stmt
  // @Override
  // public Void visitWhileStmt(Stmt.While stmt) {
  // resolve(stmt.condition);
  // resolve(stmt.body);
  // return null;
  // }
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

  // < visit-while-stmt
  // > visit-assign-expr
  @Override
  public Void visitAssignExpr(Expr.Assign expr) {
    resolve(expr.value);
    resolveLocal(expr, expr.name);
    return null;
  }

  // < visit-assign-expr
  // > visit-binary-expr
  @Override
  public Void visitBinaryExpr(Expr.Binary expr) {
    resolve(expr.left);
    resolve(expr.right);
    return null;
  }

  // < visit-binary-expr
  // > visit-call-expr
  @Override
  public Void visitCallExpr(Expr.Call expr) {
    resolve(expr.callee);

    for (Expr argument : expr.arguments) {
      resolve(argument);
    }

    return null;
  }

  // handle anonymous functions
  // @Override
  // public Void visitLambdaExpr(Expr.Lambda expr) {
  // resolveAnonymousFunction(expr, FunctionType.FUNCTION);
  // return null;
  // }

  // < visit-call-expr
  // > Classes resolver-visit-get
  @Override
  public Void visitGetExpr(Expr.Get expr) {
    resolve(expr.object);
    return null;
  }

  // < Classes resolver-visit-get
  // > visit-grouping-expr
  @Override
  public Void visitGroupingExpr(Expr.Grouping expr) {
    resolve(expr.expression);
    return null;
  }

  // < visit-grouping-expr
  // > visit-literal-expr
  @Override
  public Void visitLiteralExpr(Expr.Literal expr) {
    return null;
  }

  // < visit-literal-expr
  // > visit-logical-expr
  @Override
  public Void visitLogicalExpr(Expr.Logical expr) {
    resolve(expr.left);
    resolve(expr.right);
    return null;
  }

  // < visit-logical-expr
  // > Classes resolver-visit-set
  @Override
  public Void visitSetExpr(Expr.Set expr) {
    resolve(expr.value);
    resolve(expr.object);
    return null;
  }

  // < Classes resolver-visit-set
  // > Inheritance resolve-super-expr
  @Override
  public Void visitSuperExpr(Expr.Super expr) {
    // > invalid-super
    if (currentClass == ClassType.NONE) {
      Lox.error(expr.keyword,
          "Can't use 'super' outside of a class.");
    } else if (currentClass != ClassType.SUBCLASS) {
      Lox.error(expr.keyword,
          "Can't use 'super' in a class with no superclass.");
    }

    // < invalid-super
    resolveLocal(expr, expr.keyword);
    return null;
  }

  // < Inheritance resolve-super-expr
  // > Classes resolver-visit-this
  @Override
  public Void visitThisExpr(Expr.This expr) {
    // > this-outside-of-class
    if (currentClass == ClassType.NONE) {
      Lox.error(expr.keyword,
          "Can't use 'this' outside of a class.");
      return null;
    }

    // < this-outside-of-class
    resolveLocal(expr, expr.keyword);
    return null;
  }

  // < Classes resolver-visit-this
  // > visit-unary-expr
  @Override
  public Void visitUnaryExpr(Expr.Unary expr) {
    resolve(expr.right);
    return null;
  }

  // < visit-unary-expr
  // > visit-variable-expr
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

  // < visit-variable-expr
  // > resolve-stmt
  private void resolve(Stmt stmt) {
    stmt.accept(this);
  }

  // < resolve-stmt
  // > resolve-expr
  private void resolve(Expr expr) {
    expr.accept(this);
  }

  // < resolve-expr
  // > resolve-function
  /*
   * Resolving and Binding resolve-function < Resolving and Binding
   * set-current-function
   * private void resolveFunction(Stmt.Function function) {
   */
  // > set-current-function
  private void resolveFunction(
      Stmt.Function function, FunctionType type) {
    FunctionType enclosingFunction = currentFunction;
    currentFunction = type;

    // < set-current-function
    beginScope();
    for (Token param : function.params) {
      declare(param);
      define(param);
    }
    resolve(function.body);
    endScope();
    // > restore-current-function
    currentFunction = enclosingFunction;
    // < restore-current-function
  }

  // handle anonymous functions
  // private void resolveAnonymousFunction(
  // Expr.Lambda lambda, FunctionType type) {
  // FunctionType enclosingFunction = currentFunction;
  // currentFunction = type;
  // beginScope();
  // for (Token param : lambda.function.params) {
  // declare(param);
  // define(param);
  // }
  // resolve(lambda.function.body);
  // endScope();
  // currentFunction = enclosingFunction;
  // }

  private void resolveLoopBody(Stmt stmt) {
    startLoop();
    beginScope();
    stmt.accept(this);
    endLoop();
    endScope();
  }

  // < resolve-function
  // > begin-scope
  private void beginScope() {
    scopes.push(new HashMap<String, Boolean>());
  }

  // < begin-scope
  // > end-scope
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

  // < end-scope
  // > declare
  private void declare(Token name) {
    if (scopes.isEmpty())
      return;

    Map<String, Boolean> scope = scopes.peek();
    // > duplicate-variable
    if (scope.containsKey(name.lexeme)) {
      Lox.error(name,
          "Already a variable with this name in this scope.");
    }

    // < duplicate-variable
    scope.put(name.lexeme, false);
  }

  // < declare
  // > define
  private void define(Token name) {
    if (scopes.isEmpty())
      return;
    scopes.peek().put(name.lexeme, true);
  }

  // < define
  // > resolve-local
  private void resolveLocal(Expr expr, Token name) {
    for (int i = scopes.size() - 1; i >= 0; i--) {
      if (scopes.get(i).containsKey(name.lexeme)) {
        interpreter.resolve(expr, scopes.size() - 1 - i);
        return;
      }
    }
  }
  // < resolve-local
}
