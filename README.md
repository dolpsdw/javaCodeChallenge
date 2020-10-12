# Code Challenge Devo

## 1 Efficiently check if a string is a palindrome

Examples: ADA, ADDA

Is the problem eligible for concurrency / distribution ?
* No, the minimum functional unit for this problem will be `(a,b)=> (a === b)` does not worth the threat / distribution overheat.

Is the problem eligible for reactivity ?
* No, there is no IO/E involved.

CODE:
```
function isPalindrome(inputString){
    // Basic validation input not empty, not undefined, not null
    if(!inputString){
        return false;
    }
    if(typeof inputString !== 'string' || inputString instanceof String){
        return false;
    }
    if(inputString.length === 1){
        return true;
    }
    // ToImprove : Space trim and toUpper standarization
    const inputIsEven = (inputString.length % 2) === 0 ? true : false;
    const loopIterations = inputIsEven ? inputString.length / 2 : (inputString.length - 1) / 2;
    // Atempt to find the palindrome breaker
    for( i=0; i<loopIterations; i++){
        if(inputString[i] !== inputString[inputString.length-1-i]){
            return false;
        }
    }
    // The input is palindrome
    return true;
}
```
The code it's done inside an easy Testable Pure Function with no Side Effects.

To execute it, paste on chrome and call the function with a parameter
`isPalindrome("ADA")`

Estimated Time Efficiency: O(n/2), Estimated Space Efficiency: O(3)

## 2 Efficiently find K-complementary PAIRS

Example `(array=[1,2,3,4], k=5) => [ [0,3] , [1,2] ]`

Is the problem eligible for concurrency / distribution ?
* Could be since if we input a big array the number of calculations grow exponential, and if we define minimal functional unit as `([a,b],c) => (a+b) === c` potentially we could take advantage of concurrency for the a+b calc.
But, no. a simple sum calc is not enough "intensive" to justify concurrency / distribution.

Is the problem eligible for reactivity ?
* No, there is no IO/E involved.

To ensure Time Efficiency: we have to achieve minimum loops algorithms.
* Since we have to find all Paris, we have to iterate the array.length N
  * For each element e, will need to iterate N-e

To ensure Space Efficiency: we have to allocate the minimum memory => work with the variables that already have the data in memory.
If it is a stream like data, process the data when it is on memory.

Example array=[1,2,3,4]:
```
// Will need minimun execution line of N in this case 4
[1,2,3,4].forEach((a,b) => console.log(`val ${a}, index ${b}` ) );
// forEach will probably create new variables (a and b) foreach element in the array in an inmutable fashion. (same for reduce,map,filter...)
// so for better manage Space lets use our own for(i=0 , i<length, i++) and acces inputArray memory by index

// Inside the main for loop we have to do a minimun N-e execution loop
// for array[1] element e=2
//     will need to evaluate
//     array[1]+array[2]
//     array[1]+array[3]
// so a total of 2 iteration loop defined by N-e (4-2)
// Big total is TimeEficiency = O( N*(N-Δe) ) which is better than N^2
```

CODE:
```
function getKcomplementaryPairs(inputArray, k){
    const finalResult = [];
    // Basic validation
    if(!Array.isArray(inputArray)){
        return finalResult;
    }
    if(inputArray.length < 2){
        return finalResult;
    }
    // To improve: check k is number
    for( i= 0; i<inputArray.length; i++){
        for(j=inputArray.length-1; i<j; j--){
            if(inputArray[i]+inputArray[j] === k){
                finalResult.push([i,j]);
            }
        }
    }
    return finalResult;
}
```
The code it's done inside an easy Testable Pure Function with no Side Effects.

To execute it, paste on chrome and call the function with different parameters
`getKcomplementaryPairs([2,2,3], 5)`
`getKcomplementaryPairs([1,2,3,4], 5)`

Estimated Time Efficiency: O( N*(N-Δe) ), Estimated Space Efficiency: O(3)

## 3 Tf/idf

Example (directory=dir, terms="the sun", topN=6, periodToReport=9)=>alwaysOn

directory D will get new files (existing ones never removed or modified)
terms TT will have separate calculations of tf-idf
total tf-idf for a file = "the"TF-idf + "sun"TF-idf
report N top documents sorted by relevance.

Is the problem eligible for concurrency / distribution ?
* For serious production Big file stuff the file should be opened as a Stream and read in "small" line based chunks, we can then compose the TF-idf
* The main bottle-neck of this process is the IO HDD/SSD and is not parallelize at all.

Is the problem eligible for reactivity ?
* Yes, there is a lot of IO/E involved.
* Events in the folder should fire the process of the new file,
* All the async reads of HDD can be reacted.

Final note: For serious production Big files, i probably use battle tested libraries for IO and monitoring like 
* apache.commons.io -> for read the files
* apache.commons.io.monitor -> for monitoring the FS