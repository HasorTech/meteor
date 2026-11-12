curl --fail-with-body --max-time 5 --request POST \
{{shellHeaders}}
  --data-binary {{shellBody}} \
  {{shellUrl}}
