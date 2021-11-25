# Algo 1-3 Controller


## Development Notes
### Unable to compile
- Check that all files have package name
Files in `/script` should have project name `mrt` as package name (will cause error warning on Intellij)
- Some cats errors do not propagate. 
This happens when EitherT composition is missing type. I solved this by reducing the composition to single lines and check the compile result on Horizon Script Manager.


### Behavior
- check isDefined and 0 in params that have it
- check gap between market open / close signal between dw and ul

## Creating new Gradle project
1. Horizon > create new strategy > Generate IDEA files 
2. Go to Idea > Open Gradle Tab > Click Refresh

### Give Package Name !
