# Railway Interlocking System  
**Name:** Patel Kavyakumar Shaileshkumar  
**Subject:** Event-Driven Concurrency (EDC)  
**Project:** Programming Assignment 2 – Railway Interlocking  

---

##  Project Overview  
This project is about building a **Railway Interlocking System** that safely manages train movements on a shared track network.  
It controls which train can move and when, to make sure there are **no collisions, deadlocks, or unsafe transitions**.  

The system handles both **passenger** and **freight** trains and gives priority to passenger trains when needed.  
Each movement step is done in small simulation "ticks", so every move is checked for safety before it happens.

---

##  What the Code Does  
The main file, **`InterlockingImpl.java`**, does all the logic work:
- Keeps track of which train is on which section.  
- Finds the safest path from entry to destination.  
- Checks if a train can move without blocking or crashing another train.  
- Uses a "cooldown" system so that shared junctions don’t get used by two trains too quickly.  

There’s also:
- **`Interlocking.java`** → an interface that defines what every interlocking system must have.  


---

##  Key Features  
 Safe and collision-free train movement  
 Handles both passenger and freight trains  
 Shared junction protection (sections 3, 4, 5, 6, 10)  
 Cooldown timer for busy sections  
 Detects and avoids deadlocks automatically  
 Works well with the autograder and covers 90%+ test cases  

---

