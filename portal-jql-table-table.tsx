 const handleSort = (columnId: string) => {
    if (sortColumn === columnId) {
      setSortDirection(sortDirection === "asc" ? "desc" : "asc");
      // Sorting changes affect ordering across entire set; reset to first page
      setCurrentPage(0);
    } else {
      setSortColumn(columnId);
      setSortDirection("asc");
      // Sorting changes affect ordering across entire set; reset to first page
      setCurrentPage(0);
    }
  };


{/* Table */}
      {!isLoading && !error && (
        <div className="border border-border rounded-lg overflow-hidden bg-card">
          <Table>
            <TableHeader>
              <TableRow className="bg-muted hover:bg-muted">
                {columns.map((column) => (
                  <TableHead key={column.id}>
                    <Button
                      variant="ghost"
                      size="sm"
                      className="h-auto p-0 hover:bg-transparent font-semibold"
                      onClick={() => handleSort(column.id)}
                    >
                      {column.name}
                      <ArrowUpDown className="ml-2 h-3 w-3" />
                    </Button>
                  </TableHead>
                ))}
                <TableHead className="w-10"></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {displayIssues.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={columns.length + 1} className="text-center py-8 text-muted-foreground">
                    {debouncedSearchTerm || hasActiveFilters ? (
                      <div className="space-y-2">
                        <p>No issues found matching your search/filters</p>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => {
                            setSearchInput("");
                            setStatusFilter([]);
                            setPriorityFilter([]);
                          }}
                          className="text-primary"
                        >
                          Clear search and filters
                        </Button>
                      </div>
                    ) : (
                      <div className="space-y-1">
                        <p>No issues found</p>
                        <p className="text-xs opacity-75">
                          There are no issues matching the current query
                        </p>
                      </div>
                    )}
                  </TableCell>
                </TableRow>
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
