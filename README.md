# equests-gradle
A simple Gradle plugin to debug and package our very own
[teams-game](https://github.com/fdi2-epsilon/teams-game)
quest collections (levels).

## Usage
Add this to your quest collection buildscript and shake well:
```groovy
buildscript {
    repositories {
        ivy { url 'https://fdi2-epsilon.github.io/ivy' }
        jcenter()
    }

    dependencies {
        classpath 'eu.unipv.epsilon:equests-gradle:0.3'
    }
}

apply plugin: 'enigma-levels'
```

Keep your files ordered, just like this:
```
src/eqc
|-- "1. First quest name"
|   |-- index.html
|   |-- [other assets]
|   `-- quest-description
|-- "2. Second quest name"
|   |-- index.html
|   |-- [other assets]
|   `-- quest-description
|-- "3. The third!"
|   |-- index.html
|   |   `-- [more assets dir]
|   |       `-- something.js
|   `-- quest-description
`-- pack.png
```
> Have a `quest-description` file in each quest dir, it can be empty o contain a description for the quiz.

To set collection name and description use a `questCollection` configuration block:
```groovy
questCollection {
    name = 'My collection'
    description = '...wat? <i>Hagagagaga</i>.'
}
```

## Questions? Requests
Just fill in an issue or contact me directly :smiley:.
