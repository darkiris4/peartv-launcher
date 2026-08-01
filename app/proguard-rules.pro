# Add project specific ProGuard rules here.

# PRODUCT_SPEC.md §2.3: a shrunk/minified release build must not silently
# regress focus-motion behavior. If a release build ever ships visibly
# different (or missing) spring/tilt animation compared to debug, that's an
# R8 inlining/optimization regression in ui/motion or ui/focus — add an
# explicit -keep rule here and back it with the functional test the spec
# calls for, rather than disabling minification.
