              ) : (
                displayIssues.map((issue) => (
                  <TableRow key={issue.key} className="hover:bg-muted transition-colors">
                    {columns.map((column) => (
                      <TableCell key={column.id}>
                        {column.id === "status" ? (
                          <Badge
                            variant="outline"
                            className={cn(
                              "border",
                              getStatusColor(issue)
                            )}
                          >
                            {getIssueFieldValue(issue, column)}
                          </Badge>
                        ) : column.id === "priority" ? (
                          <Badge
                            variant="outline"
                            className={cn(
                              "border",
                              priorityColors[issue.priority] || priorityColors["Medium"]
                            )}
                          >
                            {getIssueFieldValue(issue, column)}
                          </Badge>
                        ) : column.id === "key" ? (
                          (() => {
                            const issueUrl = buildIssueUrl(issue);
                            const keyText = getIssueFieldValue(issue, column);
                            if (!issueUrl) {
                              return (
                                <span className="font-mono text-sm font-medium text-primary">
                                  {keyText}
                                </span>
                              );
                            }
                            return (
                              <a
                                href={issueUrl}
                                target="_blank"
                                rel="noreferrer"
                                className="font-mono text-sm font-medium text-primary rounded-sm px-1 transition-colors hover:bg-muted focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                                onClick={(e) => e.stopPropagation()}
                              >
                                {keyText}
                              </a>
                            );
                          })()
                        ) : (
                          <span className="text-sm" title={getIssueFieldValue(issue, column)}>
                            {column.id === "summary"
                              ? getIssueFieldValue(issue, column).length > 60
                                ? getIssueFieldValue(issue, column).substring(0, 60) + "..."
                                : getIssueFieldValue(issue, column)
                              : getIssueFieldValue(issue, column)
                            }
                          </span>
                        )}
                      </TableCell>
                    ))}
                    <TableCell>
                      {(() => {
                        const issueUrl = buildIssueUrl(issue);
                        if (!issueUrl) return null;
                        return (
                          <a
                            href={issueUrl}
                            target="_blank"
                            rel="noreferrer"
                            className="inline-flex items-center justify-center h-8 w-8 rounded-md hover:bg-muted"
                            onClick={(e) => e.stopPropagation()}
                          >
                            <ExternalLink className="h-3 w-3" />
                          </a>
                        );
                      })()}
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </div>
      )}

      {/* Pagination */}
      {!isLoading && !error && data && (
        <div className="flex items-center justify-between text-sm">
          <div className="text-muted-foreground">
            Showing {currentPage * pageSize + 1} to {Math.min((currentPage + 1) * pageSize, data.totalCount)} of {data.totalCount} results
          </div>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={!data.hasPreviousPage}
              onClick={() => handlePageChange(currentPage - 1)}
            >
              Previous
            </Button>
            <span className="px-3 py-2 text-xs text-muted-foreground">
              Page {currentPage + 1} of {Math.ceil(data.totalCount / pageSize)}
            </span>
            <Button
              variant="outline"
              size="sm"
              disabled={!data.hasNextPage}
              onClick={() => handlePageChange(currentPage + 1)}
            >
              Next
            </Button>
          </div>
        </div>
      )}

    </div>
  );
}

/**
 * Sortable Column Item for drag-and-drop reordering
 */
function SortableColumnItem({
  column,
  onRemove,
}: {
  column: ColumnConfig;
  onRemove: (id: string) => void;
}) {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
  } = useSortable({ id: column.id });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
  };

  return (
    <div
      ref={setNodeRef}
      style={style}
      className="flex items-center gap-2 p-2 bg-background border rounded-md"
    >
      <button
        type="button"
        className="cursor-grab hover:bg-muted rounded p-1"
        {...attributes}
        {...listeners}
      >
        <GripVertical className="h-4 w-4 text-muted-foreground" />
      </button>
      <span className="flex-1 text-sm">
        {column.name}
        {column.isCustom && (
          <Badge variant="secondary" className="ml-2 text-xs">Custom</Badge>
        )}
      </span>
      <button
        type="button"
        onClick={() => onRemove(column.id)}
        className="p-1 hover:bg-destructive/10 rounded text-muted-foreground hover:text-destructive"
      >
        <X className="h-4 w-4" />
      </button>
    </div>
  );
}

/**
 * Settings Panel for JQL Table Widget
 */
function JQLTableOptionsGroup() {
  const { updateComponent, selectedComponent, projectKey, serviceDeskId, isServiceDesk } = usePortalBuilderStore(
    useShallow((state) => ({
      updateComponent: state.updateComponent,
      selectedComponent: state.selectedComponent,
      projectKey: state.projectKey,
      serviceDeskId: state.serviceDeskId,
      isServiceDesk: state.isServiceDesk,
    })),
  );

  const [fieldPickerOpen, setFieldPickerOpen] = useState(false);
  const [fieldSearchQuery, setFieldSearchQuery] = useState("");

  // Fetch available fields for the project
  const configuredProjectKey = (selectedComponent?.getField("properties.projectKey") as string | undefined)?.trim();
  const effectiveProjectKey = configuredProjectKey || projectKey || "";

  const { data: fieldsData, isLoading: fieldsLoading } = useQuery({
    queryKey: ['project-fields', effectiveProjectKey],
    queryFn: () => fetchProjectFields(effectiveProjectKey),
    enabled: !!effectiveProjectKey && fieldPickerOpen,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });

  // Drag-and-drop sensors
  const sensors = useSensors(
    useSensor(PointerSensor),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    })
  );

  if (!selectedComponent) return null;

  const handleChange = (field: string, value: unknown) => {
    updateComponent(selectedComponent.id, field, value, true);
  };

  const rawColumns = selectedComponent.getField("properties.columns");
  const selectedColumns = migrateColumns(rawColumns);

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (over && active.id !== over.id) {
      const oldIndex = selectedColumns.findIndex((col) => col.id === active.id);
      const newIndex = selectedColumns.findIndex((col) => col.id === over.id);
      if (oldIndex !== -1 && newIndex !== -1) {
        const reordered = arrayMove(selectedColumns, oldIndex, newIndex);
        handleChange("properties.columns", reordered);
      }
    }
  };

  const addColumn = (field: FieldInfo) => {
    if (selectedColumns.length >= MAX_COLUMNS) return;
    if (selectedColumns.some(col => col.id === field.id)) return;

    const newColumn: ColumnConfig = {
      id: field.id,
      name: field.name,
      isCustom: field.isCustom,
    };
    handleChange("properties.columns", [...selectedColumns, newColumn]);
    setFieldPickerOpen(false);
    setFieldSearchQuery("");
  };

  const removeColumn = (columnId: string) => {
    handleChange("properties.columns", selectedColumns.filter(col => col.id !== columnId));
  };

  // Combine system and custom fields for the picker
  const allFields: FieldInfo[] = [
    ...(fieldsData?.systemFields || systemColumns.map(c => ({ id: c.id, name: c.name, category: 'system' as const, isCustom: false }))),
    ...(fieldsData?.customFields || []),
  ];

  // Filter fields based on search and exclude already selected
  const filteredFields = allFields.filter(field => {
    const isSelected = selectedColumns.some(col => col.id === field.id);
    const matchesSearch = !fieldSearchQuery ||
      field.name.toLowerCase().includes(fieldSearchQuery.toLowerCase()) ||
      field.id.toLowerCase().includes(fieldSearchQuery.toLowerCase());
    return !isSelected && matchesSearch;
  });

  const jqlPlaceholder = `project = ${(effectiveProjectKey || "PROJECT").toUpperCase()} AND reporter = currentUser() ORDER BY created DESC`;

  const useCustomerPortalLinks =
    selectedComponent.getField("properties.useCustomerPortalLinks") === "yes";

  return (
    <div className="space-y-4">
      <div className="space-y-2">
        <Label className="text-sm font-medium">Table Title</Label>
        <Input
          value={selectedComponent.getField("content") || ""}
          onChange={(e) => handleChange("content", e.target.value)}
          placeholder="Your Requests"
          className="text-sm"
        />
      </div>

      <div className="space-y-2">
        <Label className="text-sm font-medium">Subtitle (Optional)</Label>
        <Input
          value={selectedComponent.getField("description") || ""}
          onChange={(e) => handleChange("description", e.target.value)}
          placeholder="View and track your submitted requests"
          className="text-sm"
        />
      </div>

      <div className="space-y-2">
        <Label className="text-sm font-medium">JQL Query</Label>
        <Textarea
          value={selectedComponent.getField("properties.jqlQuery") || ""}
          onChange={(e) => handleChange("properties.jqlQuery", e.target.value)}
          placeholder={jqlPlaceholder}
          rows={3}
          className="font-mono text-sm"
        />
        <p className="text-xs text-muted-foreground">
          Available placeholders: {`{{PROJECT_KEY}}`}
        </p>
      </div>

      <div className="space-y-2">
        <div className="flex items-center justify-between">
          <Label className="text-sm font-medium">Columns to Display</Label>
          <span className="text-xs text-muted-foreground">
            {selectedColumns.length}/{MAX_COLUMNS} max
          </span>
        </div>

        {/* Selected columns with drag-and-drop */}
        <DndContext
          sensors={sensors}
          collisionDetection={closestCenter}
          onDragEnd={handleDragEnd}
        >
          <SortableContext
            items={selectedColumns.map((col) => col.id)}
            strategy={verticalListSortingStrategy}
          >
            <div className="space-y-1">
              {selectedColumns.map((column) => (
                <SortableColumnItem
                  key={column.id}
                  column={column}
                  onRemove={removeColumn}
                />
              ))}
            </div>
          </SortableContext>
        </DndContext>

        {/* Add column button */}
        <Popover open={fieldPickerOpen} onOpenChange={setFieldPickerOpen}>
          <PopoverTrigger asChild>
            <Button
              variant="outline"
              size="sm"
              className="w-full justify-start"
              disabled={selectedColumns.length >= MAX_COLUMNS}
            >
              <Plus className="h-4 w-4 mr-2" />
              Add Column
              {selectedColumns.length >= MAX_COLUMNS && (
                <span className="ml-auto text-xs text-muted-foreground">(max reached)</span>
              )}
            </Button>
          </PopoverTrigger>
          <PopoverContent className="w-[300px] p-0" align="start">
            <Command shouldFilter={false}>
              <CommandInput
                placeholder="Search fields..."
                value={fieldSearchQuery}
                onValueChange={setFieldSearchQuery}
              />
              <CommandList>
                {fieldsLoading && (
                  <CommandEmpty>
                    <Loader2 className="h-4 w-4 animate-spin mx-auto" />
                  </CommandEmpty>
                )}
                {!fieldsLoading && filteredFields.length === 0 && (
                  <CommandEmpty>No fields found.</CommandEmpty>
                )}
                {!fieldsLoading && filteredFields.length > 0 && (
                  <>
                    <CommandGroup heading="System Fields">
                      {filteredFields
                        .filter(f => !f.isCustom)
                        .map((field) => (
                          <CommandItem
                            key={field.id}
                            value={field.id}
                            onSelect={() => addColumn(field)}
                            className="flex items-center justify-between"
                          >
                            <span>{field.name}</span>
                          </CommandItem>
                        ))}
                    </CommandGroup>
                    {filteredFields.some(f => f.isCustom) && (
                      <CommandGroup heading="Custom Fields">
                        {filteredFields
                          .filter(f => f.isCustom)
                          .map((field) => (
                            <CommandItem
                              key={field.id}
                              value={field.id}
                              onSelect={() => addColumn(field)}
                              className="flex items-center justify-between"
                            >
                              <span>{field.name}</span>
                              <Badge variant="secondary" className="text-xs">Custom</Badge>
                            </CommandItem>
                          ))}
                      </CommandGroup>
                    )}
                  </>
                )}
              </CommandList>
            </Command>
          </PopoverContent>
        </Popover>

        <p className="text-xs text-muted-foreground">
          Drag to reorder columns. Click × to remove.
        </p>
      </div>

      <div className="flex items-center space-x-2 pt-2">
        <Checkbox
          id="showSearch"
          checked={selectedComponent.getField("properties.showSearch") !== "no"}
          onCheckedChange={(checked) =>
            handleChange("properties.showSearch", checked ? "yes" : "no")
          }
        />
        <Label
          htmlFor="showSearch"
          className="text-sm font-medium leading-none cursor-pointer"
        >
          Show Search Bar
        </Label>
      </div>

      <div className="flex items-center space-x-2">
        <Checkbox
          id="showFilter"
          checked={selectedComponent.getField("properties.showFilter") !== "no"}
          onCheckedChange={(checked) =>
            handleChange("properties.showFilter", checked ? "yes" : "no")
          }
        />
        <Label
          htmlFor="showFilter"
          className="text-sm font-medium leading-none cursor-pointer"
        >
          Show Filter Button
        </Label>
      </div>

      {/* Only show JSM toggle when service desk is configured AND it's a JSM project */}
      {serviceDeskId && isServiceDesk && (
        <div className="flex items-start space-x-2">
          <Checkbox
            id="useCustomerPortalLinks"
            checked={useCustomerPortalLinks}
            onCheckedChange={(checked) =>
              handleChange("properties.useCustomerPortalLinks", checked ? "yes" : "no")
            }
          />
          <div className="space-y-1">
            <Label
              htmlFor="useCustomerPortalLinks"
              className="text-sm font-medium leading-none cursor-pointer"
            >
              Use Customer Portal URLs
            </Label>
            <p className="text-xs text-muted-foreground">
              For Service Management projects, links will use /servicedesk/customer/portal/{serviceDeskId}/{"{ISSUE-KEY}"}
            </p>
          </div>
        </div>
      )}

      <div className="space-y-2">
        <Label className="text-sm font-medium">Results Per Page</Label>
        <Select
          value={selectedComponent.getField("properties.pageSize") || "10"}
          onValueChange={(value) => handleChange("properties.pageSize", value)}
        >
          <SelectTrigger className="text-sm">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="5">5 per page</SelectItem>
            <SelectItem value="10">10 per page</SelectItem>
            <SelectItem value="25">25 per page</SelectItem>
            <SelectItem value="50">50 per page</SelectItem>
          </SelectContent>
        </Select>
      </div>
    </div>
  );
}

export const JQLTableDesignProperties = {
  base: null,
  options: <JQLTableOptionsGroup />,
  grid: null,
  html: null,
  input: null,
  label: null,
  button: null,
  validation: null,
};

export function getReactCodeJQLTable(component: FormComponentModel): ReactCode {
  const title = component.content || "Your Requests";
  const jqlQuery = component.getField("properties.jqlQuery") || "project = {{PROJECT_KEY}} AND reporter = currentUser()";

  return {
    code: `<div className="jql-table">
  <h2>{${JSON.stringify(title)}}</h2>
  {/* JQL Query: ${jqlQuery} */}
  <Table>
    {/* Table implementation */}
  </Table>
</div>`,
    dependencies: {
      "@/components/ui/table": ["Table", "TableBody", "TableCell", "TableHead", "TableHeader", "TableRow"],
    },
  };
}
