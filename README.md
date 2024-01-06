# jlox new features

- [x] Use single quotation for string `'Some Text'`
- [x] Add multiline comment
- [x] Update string to be single line
- [x] Update number to accept `.23` as `0.23`
- [x] Allowing Block Comments to Nest
  ```c
  /* This is an outer comment.
  /* This is an inner comment. */
  Back to the outer comment. */
  ```
- [x] Edit number() function in Scanner to make this accepted:
  > 25e-1 = 2.5\
  > 25e+2 = 2500.0\
- [x] Make the access of not initialized variable not allowed:
  > stringify() in Interpreter
- [x] Accept compare number with the length of string:
  > visitBinaryExpr() in Interpreter
- [x] Accept add strings with numbers
  > visitBinaryExpr() in Interpreter
- [x] Make this:
  > print 5 \* "Hussein";\
  > output:\
  > HusseinHusseinHusseinHusseinHussein
- [x] Declaration without var
- [x] anonymous functions
  > functionDeclaration → "fun" IDENTIFIER? "(" parameters? ")" block ;
- [x] Define the "input" function for user input in Interpreter.java
- [x] catch redeclarations of variables within the global scope in Resolver.java
  > Error at 'a': Variable with this name is already declared in the global scope.
  >
  > ```c
  > var a = 5;
  > var a = 6;
  > print a;
  > ```
- [x] add break statements

#### Code from Crafting Interpreters

The code in this repository is based on the tutorial series from [craftinginterpreters.com](https://craftinginterpreters.com/) up to Chapter 11.

- If you're new to the project, consider starting with the tutorial to understand the foundations.
- For more details and the complete tutorial, visit [Crafting Interpreters](https://craftinginterpreters.com/).

Feel free to explore, contribute, and adapt the code as needed.
