# DynamicInstrScheduling

1. Type "make" to build.  (Type "make clean" first if you already compiled and want to recompile from scratch.)

2. Run trace reader:

   To run without throttling output:
   java sim 256 32 4 gcc_trace.txt

   To run with throttling (via "less"):
   java sim 256 32 4 gcc_trace.txt | less
