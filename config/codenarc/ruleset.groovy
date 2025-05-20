ruleset {

	description '''
        A Sample Groovy RuleSet containing all CodeNarc Rules, grouped by category.
        You can use this as a template for your own custom RuleSet.
        Just delete the rules that you don't want to include.
        '''

	// rulesets/basic.xml
	AssignmentInConditional
	BitwiseOperatorInConditional
	BrokenNullCheck
	ConstantAssertExpression
	ConstantIfExpression
	ConstantTernaryExpression
	DeadCode
	DoubleNegative
	DuplicateCaseStatement
	DuplicateMapKey
	DuplicateSetValue
	EmptyCatchBlock
	EmptyClass
	EmptyElseBlock
	EmptyFinallyBlock
	EmptyForStatement
	EmptyIfStatement
	EmptyInstanceInitializer
	EmptyMethod
	EmptyStaticInitializer
	EmptySwitchStatement
	EmptySynchronizedStatement
	EmptyTryBlock
	EmptyWhileStatement
	EqualsAndHashCode
	EqualsOverloaded
	ExplicitGarbageCollection
	ForLoopShouldBeWhileLoop
	HardCodedWindowsFileSeparator
	HardCodedWindowsRootDirectory
	IntegerGetInteger
	MultipleUnaryOperators
	ParameterAssignmentInFilterClosure
	RandomDoubleCoercedToZero
	RemoveAllOnSelf
	ReturnFromFinallyBlock
	ThrowExceptionFromFinallyBlock

	// rulesets/groovyism.xml
	ClosureAsLastMethodParameter
	ConfusingMultipleReturns
	ExplicitArrayListInstantiation
	ExplicitCallToPutAtMethod
	ExplicitHashMapInstantiation
	ExplicitLinkedHashMapInstantiation
	ExplicitLinkedListInstantiation
	GStringAsMapKey
	GStringExpressionWithinString
	UseCollectMany
	UseCollectNested

	// rulesets/imports.xml
	DuplicateImport
	UnusedImport
}