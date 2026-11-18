// Mensagens da API (inglês, estáveis) → português. Mensagem desconhecida vira texto genérico: nada técnico na tela.

const MESSAGES: Record<string, string> = {
  'Invalid email or password': 'E-mail ou senha incorretos.',
  'Too many failed attempts. Try again later': 'Conta bloqueada por muitas tentativas. Tente de novo mais tarde.',
  'Too many requests': 'Muitas tentativas seguidas. Aguarde um minuto.',
  'Email already in use': 'Este e-mail já tem cadastro.',
  'Password is too common. Choose a less predictable one': 'Senha muito comum. Escolha uma menos previsível.',
  'Password must not contain your name, your email or the app name': 'A senha não pode conter seu nome, seu e-mail ou o nome do app.',
  'Invalid password': 'Senha atual incorreta.',
  'New password must be different from the current one': 'A nova senha precisa ser diferente da atual.',
  'Authentication required': 'Sua sessão terminou. Entre de novo.',
  'Validation failed': 'Confira os campos destacados.',
  'Workspace not found': 'Espaço não encontrado.',
  'Only owners can do this': 'Só quem administra o espaço pode fazer isso.',
  'The last owner cannot leave the workspace': 'O espaço precisa de outra pessoa como dona antes de você sair.',
  'Invalid or expired invite': 'Convite inválido ou vencido. Peça um novo.',
  'Already a member': 'Você já participa deste espaço.',
  'Member not found': 'Pessoa não encontrada no espaço.',
  'Account not found': 'Conta não encontrada.',
  'Account is archived': 'Esta conta está arquivada.',
  'Account name already in use': 'Já existe uma conta com esse nome.',
  'Account has transactions; archive it instead': 'A conta tem lançamentos. Arquive em vez de excluir.',
  'Category not found': 'Categoria não encontrada.',
  'Category is archived': 'Esta categoria está arquivada.',
  'Category name already in use': 'Já existe uma categoria com esse nome.',
  'Category has transactions; archive it instead': 'A categoria tem lançamentos. Arquive em vez de excluir.',
  'Category is required': 'Escolha uma categoria.',
  'Category does not match the transaction type': 'A categoria não combina com o tipo do lançamento.',
  'Transfer needs a destination account': 'Escolha a conta de destino.',
  'Destination must be a different account': 'A conta de destino precisa ser outra.',
  'Date out of range': 'Data fora do intervalo permitido.',
  'Transaction not found': 'Lançamento não encontrado.',
  'Request body too large': 'Os dados enviados são grandes demais.',
  'Card not found': 'Cartão não encontrado.',
  'Recurrence not found': 'Conta fixa não encontrada.',
  'Recurrence is paused': 'Esta conta fixa está pausada.',
  'Not scheduled for this month': 'Esta conta fixa não vale para este mês.',
  'Already confirmed for this month': 'Esta conta já foi confirmada neste mês.',
  'Bill transactions keep their type': 'Lançamentos de conta fixa não mudam de tipo.',
  'Recurrences are incomes or expenses': 'Conta fixa é receita ou despesa.',
  'End month must not be before the start month': 'O fim não pode ser antes do início.',
  'Budgets are for expense categories': 'Orçamento é só para categorias de despesa.',
  'Budget not found': 'Orçamento não encontrado.',
  'Card is archived': 'Este cartão está arquivado.',
  'Card name already in use': 'Já existe um cartão com esse nome.',
  'Card has purchases; archive it instead': 'O cartão tem compras. Arquive em vez de excluir.',
  'Purchase not found': 'Compra não encontrada.',
  'Each installment must be at least R$ 0,01': 'Cada parcela precisa ser de pelo menos R$ 0,01.',
  'Card payments are made through the card statement': 'Pague a fatura pela tela do cartão.',
  'Card payments are changed through the card statement': 'Pagamentos de fatura se alteram pela tela do cartão.',
  'Rule already exists': 'Já existe uma regra com esse texto.',
  'Rule not found': 'Regra não encontrada.',
  'Pattern must have at least 2 characters': 'O texto da regra precisa de pelo menos 2 letras.',
  'Unsupported file format': 'Formato não reconhecido. Use o extrato em OFX ou CSV exportado pelo banco.',
  'No transactions found in the file': 'Nenhum lançamento encontrado no arquivo.',
  'File too large (maximum 1 MB)': 'Arquivo grande demais (máximo 1 MB).',
  'Choose a file to import': 'Escolha o arquivo do extrato.',
  'Could not read the file': 'Não foi possível ler o arquivo.',
  'Invalid upload': 'Não foi possível enviar o arquivo.',
  'Imported lines are incomes or expenses': 'Cada linha importada é receita ou despesa.',
  'Telegram is not configured': 'O Telegram não está configurado neste servidor.',
}

// Mensagens com número (linha ou transação do arquivo)
const PATTERNS: [RegExp, (m: RegExpMatchArray) => string][] = [
  [/^Invalid date on line (\d+)$/, (m) => `Data inválida na linha ${m[1]} do arquivo.`],
  [/^Invalid amount on line (\d+)$/, (m) => `Valor inválido na linha ${m[1]} do arquivo.`],
  [/^Invalid date in transaction (\d+)$/, (m) => `Data inválida no lançamento ${m[1]} do arquivo.`],
  [/^Invalid amount in transaction (\d+)$/, (m) => `Valor inválido no lançamento ${m[1]} do arquivo.`],
  [/^Too many lines \(maximum (\d+)\)$/, (m) => `O arquivo tem lançamentos demais (máximo ${m[1]}). Exporte um período menor.`],
]

export const GENERIC_ERROR = 'Algo deu errado. Tente de novo.'

export function translate(message: string | undefined): string {
  if (!message) return GENERIC_ERROR
  if (MESSAGES[message]) return MESSAGES[message]
  for (const [re, fn] of PATTERNS) {
    const m = message.match(re)
    if (m) return fn(m)
  }
  return GENERIC_ERROR
}
