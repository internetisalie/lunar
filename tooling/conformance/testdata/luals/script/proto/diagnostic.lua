local m = {}

m.register {
    'need-check-nil',
    'undefined-field',
    'cast-local-type',
    'assign-type-mismatch',
    'param-type-mismatch',
    'cast-type-mismatch',
    'return-type-mismatch',
    'inject-field',
} {
    group    = 'type-check',
    severity = 'Warning',
}

m.register {
    'redundant-value',
    'unbalanced-assignments',
    'redundant-parameter',
    'missing-parameter',
    'missing-return-value',
    'redundant-return-value',
    'missing-return',
    'missing-fields',
} {
    group    = 'unbalanced',
    severity = 'Warning',
}

m.register {
    'unused-local',
} {
    group    = 'unused',
    severity = 'Hint',
}

return m
